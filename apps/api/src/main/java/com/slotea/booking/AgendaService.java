package com.slotea.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.booking.AgendaDtos.AgendaAppointment;
import com.slotea.booking.AgendaDtos.AgendaCustomer;
import com.slotea.booking.AgendaDtos.AgendaResponse;
import com.slotea.booking.AgendaDtos.AgendaStaff;
import com.slotea.booking.AppointmentNotifications.Change;
import com.slotea.booking.Customers.Contact;
import com.slotea.business.Business;
import com.slotea.business.BusinessRepository;
import com.slotea.business.StaffMember;
import com.slotea.business.StaffMemberRepository;
import com.slotea.business.StaffService;
import com.slotea.catalog.Offering;
import com.slotea.catalog.OfferingRepository;
import com.slotea.notification.EmailType;
import com.slotea.shared.security.Viewer;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;

/**
 * La agenda del panel: ver las citas por días y gestionarlas desde el negocio.
 *
 * <ul>
 * <li><strong>El dueño</strong> ve y gestiona todas las citas del negocio.</li>
 * <li><strong>Un empleado</strong>, solo las de su ficha de profesional (la que el dueño ha enlazado a su
 *     usuario). Lo decide el servidor, no un filtro que mande el cliente: la cita de otro profesional da
 *     404, igual que la de otro negocio. Sin ficha enlazada, su agenda está vacía.</li>
 * <li>Los días se cuentan en la zona horaria del negocio: «el martes» va de las 00:00 a las 00:00 del
 *     miércoles de esa zona, aunque ese día dure 23 o 25 horas por el cambio de hora.</li>
 * <li>Cancelar desde el panel avisa al cliente por email (la misma outbox de siempre), sin el límite de
 *     2 horas del enlace del cliente: el negocio puede cancelar hasta que empieza la cita.</li>
 * <li>Marcar cómo acabó (completada o no presentada) solo tiene sentido cuando la cita ya ha empezado, y
 *     se puede corregir. No manda emails.</li>
 * </ul>
 */
@Service
class AgendaService {

    /** Seis semanas: una vista de mes completa. Lo bastante para no cargar años de citas de golpe. */
    static final int MAX_DAYS = 42;

    static final String NOT_FOUND = "No existe esa cita.";
    static final String ONLY_OWN = "Solo puedes ver tu propia agenda.";
    static final String CANCELLED = "Esta cita está cancelada.";
    static final String NOT_STARTED = "La cita aún no ha empezado.";
    static final String ALREADY_STARTED = "La cita ya ha empezado: márcala como completada o no presentada.";
    static final String FINISHED = "Esta cita ya ha terminado: no se puede cancelar.";
    static final String CHANGED_MEANWHILE = "La cita acaba de cambiar. Recarga la agenda.";

    private final AppointmentRepository appointments;
    private final Customers customers;
    private final BusinessRepository businesses;
    private final OfferingRepository offerings;
    private final StaffMemberRepository staffRepository;
    private final StaffService staff;
    private final AppointmentNotifications notifications;
    private final TransactionTemplate tx;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    AgendaService(AppointmentRepository appointments, Customers customers, BusinessRepository businesses,
            OfferingRepository offerings, StaffMemberRepository staffRepository, StaffService staff,
            AppointmentNotifications notifications, PlatformTransactionManager transactionManager, Clock clock) {
        this.appointments = appointments;
        this.customers = customers;
        this.businesses = businesses;
        this.offerings = offerings;
        this.staffRepository = staffRepository;
        this.staff = staff;
        this.notifications = notifications;
        this.tx = new TransactionTemplate(transactionManager);
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
    }

    AgendaResponse agenda(Viewer viewer, LocalDate from, LocalDate to, @Nullable UUID staffId) {
        if (to.isBefore(from)) {
            throw ApiException.invalidField("to", "debe ser igual o posterior a la fecha de inicio");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw ApiException.invalidField("to", "como mucho " + MAX_DAYS + " días de agenda de una vez");
        }
        return Objects.requireNonNull(readOnly.execute(_ -> {
            Business business = business();
            ZoneId zone = ZoneId.of(business.getTimezone());
            Instant start = from.atStartOfDay(zone).toInstant();
            Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

            List<StaffMember> allStaff = staffRepository.findAllOrdered();
            UUID only = scope(viewer, staffId, allStaff);
            if (!viewer.owner() && only == null) {
                // Empleado sin ficha enlazada: no hay agenda que enseñarle.
                return new AgendaResponse(zone.getId(), from, to, List.of(), List.of());
            }
            List<Appointment> found = only == null
                    ? appointments.findStartingBetween(start, end)
                    : appointments.findStartingBetweenForStaff(only, start, end);

            Set<UUID> withAppointments = found.stream().map(Appointment::getStaffId).collect(Collectors.toSet());
            List<AgendaStaff> columns = allStaff.stream()
                    .filter(s -> only == null ? s.isActive() || withAppointments.contains(s.getId())
                            : s.getId().equals(only))
                    .map(s -> new AgendaStaff(s.getId(), s.getDisplayName(), s.isActive()))
                    .toList();
            return new AgendaResponse(zone.getId(), from, to, columns, views(found, zone, allStaff));
        }));
    }

    AgendaAppointment get(Viewer viewer, UUID id) {
        return Objects.requireNonNull(readOnly.execute(_ -> view(load(viewer, id))));
    }

    AgendaAppointment recordOutcome(Viewer viewer, UUID id, AppointmentStatus outcome) {
        if (outcome == AppointmentStatus.CANCELLED) {
            throw ApiException.invalidField("status", "para cancelar una cita, usa «cancelar»");
        }
        try {
            return Objects.requireNonNull(tx.execute(_ -> {
                Appointment appointment = load(viewer, id);
                if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
                    throw ApiException.conflict(CANCELLED);
                }
                if (clock.instant().isBefore(appointment.getStartsAt())) {
                    throw ApiException.conflict(NOT_STARTED);
                }
                if (appointment.getStatus() != outcome) {
                    appointment.recordOutcome(outcome);
                    appointments.saveAndFlush(appointment);
                }
                return view(appointment);
            }));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict(CHANGED_MEANWHILE);
        }
    }

    /** Cancela y avisa al cliente. Repetirlo no hace nada (ni manda otro email). */
    AgendaAppointment cancel(Viewer viewer, UUID id) {
        record Cancelled(AgendaAppointment view, List<UUID> emailIds) {
        }
        Cancelled cancelled;
        try {
            cancelled = Objects.requireNonNull(tx.execute(_ -> {
                Appointment appointment = load(viewer, id);
                switch (appointment.getStatus()) {
                    case CANCELLED -> {
                        return new Cancelled(view(appointment), List.of());
                    }
                    case COMPLETED, NO_SHOW -> throw ApiException.conflict(FINISHED);
                    case CONFIRMED -> {
                        if (!clock.instant().isBefore(appointment.getStartsAt())) {
                            throw ApiException.conflict(ALREADY_STARTED);
                        }
                    }
                }
                appointment.cancel(clock.instant());
                appointments.saveAndFlush(appointment);
                // Al cliente sí; al negocio no: lo acaba de hacer él.
                List<UUID> emailIds = notifications.enqueue(new Change(EmailType.BOOKING_CANCELLED, null, null),
                        business(), appointment, customers.contact(appointment.getCustomerId()),
                        serviceName(appointment), staff.require(appointment.getStaffId()).getDisplayName());
                return new Cancelled(view(appointment), emailIds);
            }));
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ApiException.conflict(CHANGED_MEANWHILE); // p. ej. el cliente la ha movido a la vez
        }
        notifications.sendNow(cancelled.emailIds());
        return cancelled.view();
    }

    /**
     * El profesional cuyas citas se ven: para un empleado, siempre el suyo ({@code null} si no tiene
     * ficha); para el dueño, el que pida (o {@code null} = todos).
     */
    private @Nullable UUID scope(Viewer viewer, @Nullable UUID requested, List<StaffMember> allStaff) {
        if (viewer.owner()) {
            if (requested != null && allStaff.stream().noneMatch(s -> s.getId().equals(requested))) {
                throw ApiException.notFound("No existe ese profesional.");
            }
            return requested;
        }
        UUID own = staff.linkedTo(viewer.userId()).map(StaffMember::getId).orElse(null);
        if (requested != null && !requested.equals(own)) {
            throw ApiException.forbidden(ONLY_OWN);
        }
        return own;
    }

    /** La cita, si quien pregunta puede verla. Si no, 404: no revelamos que existe. */
    private Appointment load(Viewer viewer, UUID id) {
        Appointment appointment = appointments.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        if (!viewer.owner()) {
            boolean own = staff.linkedTo(viewer.userId())
                    .map(s -> s.getId().equals(appointment.getStaffId()))
                    .orElse(false);
            if (!own) {
                throw ApiException.notFound(NOT_FOUND);
            }
        }
        return appointment;
    }

    private List<AgendaAppointment> views(List<Appointment> found, ZoneId zone, List<StaffMember> allStaff) {
        if (found.isEmpty()) {
            return List.of();
        }
        Set<UUID> serviceIds = new HashSet<>();
        Set<UUID> customerIds = new HashSet<>();
        for (Appointment a : found) {
            serviceIds.add(a.getServiceId());
            customerIds.add(a.getCustomerId());
        }
        Map<UUID, String> serviceNames = offerings.findAllById(serviceIds).stream()
                .collect(Collectors.toMap(Offering::getId, Offering::getName));
        Map<UUID, String> staffNames = allStaff.stream()
                .collect(Collectors.toMap(StaffMember::getId, StaffMember::getDisplayName));
        Map<UUID, Contact> contacts = customers.contacts(customerIds);
        Instant now = clock.instant();
        return found.stream()
                .map(a -> view(a, zone, now, serviceNames::get, staffNames::get, contacts::get))
                .toList();
    }

    private AgendaAppointment view(Appointment appointment) {
        ZoneId zone = ZoneId.of(business().getTimezone());
        return view(appointment, zone, clock.instant(), _ -> serviceName(appointment),
                _ -> staff.require(appointment.getStaffId()).getDisplayName(),
                _ -> customers.contact(appointment.getCustomerId()));
    }

    private static AgendaAppointment view(Appointment a, ZoneId zone, Instant now, Function<UUID, String> serviceName,
            Function<UUID, String> staffName, Function<UUID, Contact> contact) {
        var start = a.getStartsAt().atZone(zone);
        Contact customer = contact.apply(a.getCustomerId());
        boolean started = !now.isBefore(a.getStartsAt());
        return new AgendaAppointment(a.getId(), a.getStatus(), a.getStaffId(), staffName.apply(a.getStaffId()),
                a.getServiceId(), serviceName.apply(a.getServiceId()),
                new AgendaCustomer(customer.fullName(), customer.email(), customer.phone()), a.getStartsAt(),
                a.getEndsAt(), start.toLocalDate(), start.toLocalTime(), a.getEndsAt().atZone(zone).toLocalTime(),
                a.getNotes(), a.getCreatedAt(), a.getCancelledAt(),
                a.getStatus() == AppointmentStatus.CONFIRMED && !started,
                a.getStatus() != AppointmentStatus.CANCELLED && started);
    }

    private Business business() {
        return businesses.findById(TenantContext.requireBusinessId())
                .orElseThrow(() -> ApiException.notFound(NOT_FOUND));
    }

    // Servicios y profesionales se desactivan, no se borran: la cita siempre encuentra los suyos.
    private String serviceName(Appointment appointment) {
        return offerings.findById(appointment.getServiceId()).map(Offering::getName).orElseThrow();
    }
}
