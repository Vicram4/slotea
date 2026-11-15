package com.slotea.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.booking.AppointmentNotifications.Change;
import com.slotea.booking.AvailabilityService.BookableSlot;
import com.slotea.booking.PublicDtos.AvailabilityResponse;
import com.slotea.booking.PublicDtos.ManagedAppointmentResponse;
import com.slotea.booking.PublicDtos.RescheduleRequest;
import com.slotea.business.Business;
import com.slotea.business.BusinessRepository;
import com.slotea.business.StaffMember;
import com.slotea.business.StaffMemberRepository;
import com.slotea.catalog.Offering;
import com.slotea.catalog.OfferingRepository;
import com.slotea.notification.EmailType;
import com.slotea.shared.security.OpaqueTokens;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;

/**
 * Lo que puede hacer un invitado con el enlace de su cita: verla, cancelarla o cambiarla de hora.
 *
 * <ul>
 * <li>El token se busca por su hash y <strong>dentro del negocio del slug</strong> ({@code @TenantId}):
 *     el enlace de otro negocio da 404, igual que un token inventado.</li>
 * <li>Cancelar o cambiar solo se puede con la cita confirmada y hasta {@link BookingPolicy#CHANGE_DEADLINE}
 *     antes de empezar. Cancelar dos veces no es un error (el segundo clic no hace nada).</li>
 * <li>Cambiar de hora mueve <em>la misma fila</em> (conserva id y enlace) con las mismas reglas que una
 *     reserva nueva: el hueco tiene que ser uno de los ofrecidos y, si dos personas van a por él a la
 *     vez, la restricción de la BD deja pasar a una sola.</li>
 * <li>Cada cambio encola sus emails (al cliente y el aviso al negocio) en la misma transacción y los
 *     envía después del commit.</li>
 * </ul>
 */
@Service
class ManageAppointmentService {

    static final String NOT_FOUND = "Este enlace no es válido o la cita ya no existe.";
    static final String TOO_LATE = "Faltan menos de 2 horas para la cita: ya no se puede cambiar ni cancelar desde aquí. Contacta con el negocio.";
    static final String CANCELLED = "Esta cita está cancelada.";
    static final String FINISHED = "Esta cita ya no se puede cambiar.";
    static final String CHANGED_MEANWHILE = "La cita acaba de cambiar. Recarga la página.";

    /** Lo que generan {@code AppointmentLinks}: 32 bytes en Base64url. Otra cosa ni se busca. */
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final AppointmentRepository appointments;
    private final AvailabilityService availability;
    private final BookingService bookings;
    private final Customers customers;
    private final BusinessRepository businesses;
    private final OfferingRepository offerings;
    private final StaffMemberRepository staff;
    private final AppointmentNotifications notifications;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    ManageAppointmentService(AppointmentRepository appointments, AvailabilityService availability,
            BookingService bookings, Customers customers, BusinessRepository businesses, OfferingRepository offerings,
            StaffMemberRepository staff, AppointmentNotifications notifications,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.appointments = appointments;
        this.availability = availability;
        this.bookings = bookings;
        this.customers = customers;
        this.businesses = businesses;
        this.offerings = offerings;
        this.staff = staff;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    ManagedAppointmentResponse get(String token) {
        return Objects.requireNonNull(readOnly.execute(_ -> view(load(token))));
    }

    ManagedAppointmentResponse cancel(String token) {
        Changed changed = Objects.requireNonNull(tx.execute(_ -> {
            Appointment appointment = load(token);
            if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
                return new Changed(view(appointment), List.of()); // segundo clic: nada que hacer, ni email
            }
            requireChangeable(appointment);
            appointment.cancel(clock.instant());
            appointments.saveAndFlush(appointment);
            List<UUID> emailIds = enqueue(
                    new Change(EmailType.BOOKING_CANCELLED, EmailType.BUSINESS_BOOKING_CANCELLED, null), appointment);
            return new Changed(view(appointment), emailIds);
        }));
        return sent(changed);
    }

    /** Huecos para mover la cita (del mismo servicio). La cita no se bloquea a sí misma. */
    AvailabilityResponse availability(String token, @Nullable UUID staffId, LocalDate from, LocalDate to) {
        Appointment appointment = Objects.requireNonNull(readOnly.execute(_ -> load(token)));
        requireChangeable(appointment);
        return availability.availability(appointment.getServiceId(), staffId, from, to, appointment.getId());
    }

    ManagedAppointmentResponse reschedule(String token, RescheduleRequest request) {
        Appointment current = Objects.requireNonNull(readOnly.execute(_ -> load(token)));
        requireChangeable(current);
        if (request.startsAt().equals(current.getStartsAt())
                && (request.staffId() == null || request.staffId().equals(current.getStaffId()))) {
            return get(token); // misma hora: no hay nada que mover
        }
        BookableSlot slot = availability.findSlot(current.getServiceId(), request.staffId(), request.startsAt(),
                current.getId());
        Changed changed;
        try {
            changed = bookings.withFirstFreeStaff(slot.staff(), member -> move(token, current, slot, member))
                    .orElseThrow(() -> ApiException.conflict(BookingService.SLOT_TAKEN));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict(CHANGED_MEANWHILE); // otra pestaña la ha cambiado a la vez
        }
        return sent(changed);
    }

    private Changed move(String token, Appointment before, BookableSlot slot, StaffMember member) {
        Appointment appointment = load(token); // fresca, en esta transacción
        if (!appointment.getStartsAt().equals(before.getStartsAt())) {
            throw ApiException.conflict(CHANGED_MEANWHILE);
        }
        requireChangeable(appointment);
        Offering service = slot.service();
        Instant endsAt = slot.startsAt().plus(Duration.ofMinutes(service.getDurationMin()));
        Instant blockedUntil = endsAt.plus(Duration.ofMinutes(service.getBufferMin()));
        appointment.moveTo(member.getId(), slot.startsAt(), endsAt, blockedUntil);
        // saveAndFlush: el UPDATE (y la posible violación 23P01) ocurre dentro del intento.
        appointments.saveAndFlush(appointment);
        List<UUID> emailIds = enqueue(new Change(EmailType.BOOKING_RESCHEDULED,
                EmailType.BUSINESS_BOOKING_RESCHEDULED, before.getStartsAt()), appointment);
        return new Changed(view(appointment), emailIds);
    }

    private record Changed(ManagedAppointmentResponse view, List<UUID> emailIds) {
    }

    private ManagedAppointmentResponse sent(Changed changed) {
        notifications.sendNow(changed.emailIds()); // ya confirmado: si falla, lo reintenta el job
        return changed.view();
    }

    private Appointment load(String token) {
        if (!TOKEN.matcher(token).matches()) {
            throw ApiException.notFound(NOT_FOUND);
        }
        return appointments.findByManageTokenHash(OpaqueTokens.sha256(token))
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    private void requireChangeable(Appointment appointment) {
        switch (appointment.getStatus()) {
            case CANCELLED -> throw ApiException.conflict(CANCELLED);
            case COMPLETED, NO_SHOW -> throw ApiException.conflict(FINISHED);
            case CONFIRMED -> {
                if (!clock.instant().isBefore(deadline(appointment))) {
                    throw ApiException.conflict(TOO_LATE);
                }
            }
        }
    }

    private static Instant deadline(Appointment appointment) {
        return appointment.getStartsAt().minus(BookingPolicy.CHANGE_DEADLINE);
    }

    private List<UUID> enqueue(Change change, Appointment appointment) {
        return notifications.enqueue(change, business(), appointment, customers.contact(appointment.getCustomerId()),
                serviceName(appointment), staffName(appointment));
    }

    private ManagedAppointmentResponse view(Appointment appointment) {
        ZoneId zone = ZoneId.of(business().getTimezone());
        var start = appointment.getStartsAt().atZone(zone);
        boolean changeable = appointment.getStatus() == AppointmentStatus.CONFIRMED
                && clock.instant().isBefore(deadline(appointment));
        return new ManagedAppointmentResponse(appointment.getId(), appointment.getStatus(),
                appointment.getServiceId(), serviceName(appointment), appointment.getStaffId(),
                staffName(appointment), customers.contact(appointment.getCustomerId()).fullName(),
                appointment.getStartsAt(), appointment.getEndsAt(), start.toLocalDate(), start.toLocalTime(),
                appointment.getEndsAt().atZone(zone).toLocalTime(), zone.getId(), changeable, deadline(appointment));
    }

    private Business business() {
        return businesses.findById(TenantContext.requireBusinessId())
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    // Servicios y profesionales se desactivan, no se borran: la cita siempre encuentra los suyos.
    private String serviceName(Appointment appointment) {
        return offerings.findById(appointment.getServiceId()).map(Offering::getName).orElseThrow();
    }

    private String staffName(Appointment appointment) {
        return staff.findById(appointment.getStaffId()).map(StaffMember::getDisplayName).orElseThrow();
    }
}
