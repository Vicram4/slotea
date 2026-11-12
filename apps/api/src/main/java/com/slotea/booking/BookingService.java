package com.slotea.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.booking.AvailabilityService.BookableSlot;
import com.slotea.booking.PublicDtos.BookingRequest;
import com.slotea.booking.PublicDtos.BookingResponse;
import com.slotea.business.StaffMember;
import com.slotea.shared.security.OpaqueTokens;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;
import com.slotea.shared.web.DatabaseErrors;

/**
 * Crea citas. Las reglas (horario, cierres, antelación…) las comprueba {@link AvailabilityService};
 * que nadie más se lleve el hueco a la vez lo garantiza la BD ({@code appointment_no_overlap}).
 *
 * <p><strong>Una transacción por intento.</strong> Con «cualquier profesional», si el primero se
 * ocupa justo ahora probamos con el siguiente. En PostgreSQL, tras un error la transacción queda
 * abortada y no admite más sentencias: por eso cada intento abre la suya, y este servicio no es
 * {@code @Transactional}.
 *
 * <p><strong>Cola por profesional.</strong> Sin más, varias reservas simultáneas del mismo hueco
 * acaban en <em>deadlocks</em>: al comprobar un {@code EXCLUDE}, PostgreSQL inserta primero en el índice
 * y luego busca conflictos, así que cada transacción ve la fila sin confirmar de la otra y la espera.
 * PostgreSQL rompe el ciclo abortando a una (40P01), pero solo lo detecta tras {@code deadlock_timeout}
 * (1 s) y de uno en uno: con 20 clientes, el último esperaría ~19 s. Por eso cada intento bloquea
 * antes la fila del profesional ({@code FOR NO KEY UPDATE}): los que compiten por él hacen cola, el
 * segundo espera solo a que el primero confirme y recibe un 23P01 limpio.
 *
 * <p>El bloqueo solo <em>ordena</em>; la garantía sigue siendo la restricción de la BD (ver
 * {@code BookingConcurrencyTests}). Un 40P01 se sigue tratando como «ocupado», por si acaso: en un
 * deadlock siempre gana al menos una transacción.
 */
@Service
class BookingService {

    static final String SLOT_TAKEN = "Ese hueco acaba de ocuparse. Elige otra hora.";
    private static final String NO_OVERLAP = "appointment_no_overlap";
    private static final String DEADLOCK = "40P01";

    private final AvailabilityService availability;
    private final Customers customers;
    private final AppointmentRepository appointments;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    BookingService(AvailabilityService availability, Customers customers, AppointmentRepository appointments,
            JdbcClient jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        this.availability = availability;
        this.customers = customers;
        this.appointments = appointments;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    BookingResponse book(BookingRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Each booking attempt needs its own transaction");
        }
        BookableSlot slot = availability.findSlot(request.serviceId(), request.staffId(), request.startsAt());
        var customer = request.customer();
        String email = customer.email().strip().toLowerCase(Locale.ROOT);
        String fullName = customer.fullName().strip();
        String phone = clean(customer.phone());
        String notes = clean(request.notes());

        for (StaffMember staff : slot.staff()) {
            try {
                return Objects.requireNonNull(tx.execute(_ -> {
                    queueFor(staff);
                    UUID customerId = customers.findOrCreate(email, fullName, phone);
                    return create(slot, staff, customerId, email, notes);
                }));
            } catch (DataAccessException e) {
                if (!slotTaken(e)) {
                    throw e;
                }
                // Otro cliente se ha llevado a este profesional en este instante: probamos con el siguiente.
            }
        }
        throw ApiException.conflict(SLOT_TAKEN);
    }

    private BookingResponse create(BookableSlot slot, StaffMember staff, UUID customerId, String email,
            @Nullable String notes) {
        var service = slot.service();
        Instant endsAt = slot.startsAt().plus(Duration.ofMinutes(service.getDurationMin()));
        Instant blockedUntil = endsAt.plus(Duration.ofMinutes(service.getBufferMin()));
        // El token del enlace para gestionar la cita se envía por email en la fase 7. Solo guardamos su hash.
        byte[] tokenHash = OpaqueTokens.sha256(OpaqueTokens.generate());
        var appointment = new Appointment(staff.getId(), service.getId(), customerId, slot.startsAt(), endsAt,
                blockedUntil, tokenHash, notes, clock.instant());
        // saveAndFlush: el INSERT (y la posible violación 23P01) ocurre aquí, dentro del try del llamante.
        appointment = appointments.saveAndFlush(appointment);
        var local = slot.startsAt().atZone(slot.zone());
        return new BookingResponse(appointment.getId(), service.getName(), staff.getDisplayName(),
                appointment.getStartsAt(), appointment.getEndsAt(), local.toLocalDate(), local.toLocalTime(),
                slot.zone().getId(), email);
    }

    /** Cola por profesional hasta el final de la transacción (ver la documentación de la clase). */
    private void queueFor(StaffMember staff) {
        // NO KEY UPDATE no choca con el FOR KEY SHARE de las FKs: solo hace esperar a otras reservas.
        jdbc.sql("SELECT id FROM staff_member WHERE business_id = ? AND id = ? FOR NO KEY UPDATE")
                .params(TenantContext.requireBusinessId(), staff.getId())
                .query(UUID.class)
                .single();
    }

    private static boolean slotTaken(DataAccessException e) {
        return NO_OVERLAP.equals(DatabaseErrors.violatedConstraint(e)) || DEADLOCK.equals(DatabaseErrors.sqlState(e));
    }

    private static @Nullable String clean(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
