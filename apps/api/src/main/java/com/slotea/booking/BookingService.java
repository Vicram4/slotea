package com.slotea.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

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
import com.slotea.business.Business;
import com.slotea.business.StaffMember;
import com.slotea.booking.AppointmentNotifications.Change;
import com.slotea.notification.EmailType;
import com.slotea.shared.links.AppointmentLinks;
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
 *
 * <p><strong>Emails.</strong> La confirmación al cliente y el aviso al negocio se encolan en la misma
 * transacción que la cita (outbox) y se envían después del commit; si el SMTP falla, la reserva sigue
 * siendo válida y el job los reintenta.
 */
@Service
class BookingService {

    static final String SLOT_TAKEN = "Ese hueco acaba de ocuparse. Elige otra hora.";
    private static final String NO_OVERLAP = "appointment_no_overlap";
    private static final String DEADLOCK = "40P01";

    private final AvailabilityService availability;
    private final Customers customers;
    private final AppointmentRepository appointments;
    private final AppointmentLinks links;
    private final AppointmentNotifications notifications;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    BookingService(AvailabilityService availability, Customers customers, AppointmentRepository appointments,
            AppointmentLinks links, AppointmentNotifications notifications, JdbcClient jdbc, PlatformTransactionManager transactionManager,
            Clock clock) {
        this.availability = availability;
        this.customers = customers;
        this.appointments = appointments;
        this.links = links;
        this.notifications = notifications;
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

        Booked booked = withFirstFreeStaff(slot.staff(), staff -> {
            UUID customerId = customers.findOrCreate(email, fullName, phone);
            return create(slot, staff, customerId, email, notes);
        }).orElseThrow(() -> ApiException.conflict(SLOT_TAKEN));
        notifications.sendNow(booked.emailIds()); // ya confirmada: si falla, lo reintenta el job
        return booked.response();
    }

    /**
     * Prueba {@code attempt} con cada profesional, en orden: cada intento en su propia transacción y
     * en la cola de ese profesional. Devuelve el resultado del primero que no choca con otra cita, o
     * vacío si todos estaban ocupados. Lo usan la reserva y el cambio de hora.
     */
    <T> Optional<T> withFirstFreeStaff(List<StaffMember> candidates, Function<StaffMember, T> attempt) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Each booking attempt needs its own transaction");
        }
        for (StaffMember staff : candidates) {
            try {
                return Optional.of(Objects.requireNonNull(tx.execute(_ -> {
                    queueFor(staff);
                    return attempt.apply(staff);
                })));
            } catch (DataAccessException e) {
                if (!slotTaken(e)) {
                    throw e;
                }
                // Otro cliente se ha llevado a este profesional en este instante: probamos con el siguiente.
            }
        }
        return Optional.empty();
    }

    private record Booked(BookingResponse response, List<UUID> emailIds) {
    }

    private Booked create(BookableSlot slot, StaffMember staff, UUID customerId, String email,
            @Nullable String notes) {
        var service = slot.service();
        Instant endsAt = slot.startsAt().plus(Duration.ofMinutes(service.getDurationMin()));
        Instant blockedUntil = endsAt.plus(Duration.ofMinutes(service.getBufferMin()));
        // Solo guardamos el hash del token del enlace; el token se recalcula al enviar el email.
        UUID id = Appointment.newId();
        var appointment = new Appointment(id, staff.getId(), service.getId(), customerId, slot.startsAt(), endsAt,
                blockedUntil, links.tokenHash(id), notes, clock.instant());
        // saveAndFlush: el INSERT (y la posible violación 23P01) ocurre aquí, dentro del try del llamante.
        appointment = appointments.saveAndFlush(appointment);

        Business business = slot.business();
        // Los datos guardados del cliente (si ya existía, no se sobrescriben con los de esta reserva).
        List<UUID> emailIds = notifications.enqueue(
                new Change(EmailType.BOOKING_CONFIRMED, EmailType.BUSINESS_NEW_BOOKING, null), business,
                appointment, customers.contact(customerId), service.getName(), staff.getDisplayName());

        var local = slot.startsAt().atZone(slot.zone());
        return new Booked(new BookingResponse(appointment.getId(), service.getName(), staff.getDisplayName(),
                appointment.getStartsAt(), appointment.getEndsAt(), local.toLocalDate(), local.toLocalTime(),
                slot.zone().getId(), email, links.token(id)), emailIds);
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
