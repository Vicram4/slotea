package com.slotea.booking;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotea.booking.PublicDtos.AvailabilityResponse;
import com.slotea.booking.PublicDtos.DayAvailability;
import com.slotea.booking.PublicDtos.SlotResponse;
import com.slotea.business.Business;
import com.slotea.business.BusinessRepository;
import com.slotea.business.StaffMember;
import com.slotea.business.StaffService;
import com.slotea.catalog.Offering;
import com.slotea.catalog.OfferingRepository;
import com.slotea.schedule.AvailabilityCalculator;
import com.slotea.schedule.AvailabilityCalculator.Busy;
import com.slotea.schedule.AvailabilityCalculator.Query;
import com.slotea.schedule.AvailabilityCalculator.Segment;
import com.slotea.schedule.AvailabilityCalculator.Slot;
import com.slotea.schedule.AvailabilityCalculator.StaffCalendar;
import com.slotea.schedule.Closure;
import com.slotea.schedule.ClosureRepository;
import com.slotea.schedule.WorkingHours;
import com.slotea.schedule.WorkingHoursRepository;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;

/**
 * Carga lo que necesita {@link AvailabilityCalculator} (horarios, cierres y citas) del negocio de la
 * petición y le pregunta. Es la única fuente de verdad sobre qué se puede reservar: la usan tanto la
 * lista de huecos como la creación de la cita.
 */
@Service
@Transactional(readOnly = true)
class AvailabilityService {

    static final String SLOT_UNAVAILABLE = "Esa hora ya no está disponible. Elige otra.";

    private final BusinessRepository businesses;
    private final OfferingRepository offerings;
    private final StaffService staffService;
    private final WorkingHoursRepository hours;
    private final ClosureRepository closures;
    private final AppointmentRepository appointments;
    private final Clock clock;

    AvailabilityService(BusinessRepository businesses, OfferingRepository offerings, StaffService staffService,
            WorkingHoursRepository hours, ClosureRepository closures, AppointmentRepository appointments,
            Clock clock) {
        this.businesses = businesses;
        this.offerings = offerings;
        this.staffService = staffService;
        this.hours = hours;
        this.closures = closures;
        this.appointments = appointments;
        this.clock = clock;
    }

    /** Un hueco concreto que se puede reservar, con sus profesionales libres en el orden en que probarlos. */
    record BookableSlot(Offering service, ZoneId zone, Instant startsAt, List<StaffMember> staff) {
    }

    AvailabilityResponse availability(UUID serviceId, @Nullable UUID staffId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.invalidField("to", "debe ser igual o posterior a from");
        }
        if (from.plusDays(BookingPolicy.MAX_RANGE_DAYS - 1).isBefore(to)) {
            throw ApiException.invalidField("to", "como mucho " + BookingPolicy.MAX_RANGE_DAYS + " días por consulta");
        }
        Request request = load(serviceId, staffId);
        LocalDate today = LocalDate.now(clock.withZone(request.zone()));
        LocalDate first = from.isBefore(today) ? today : from;
        LocalDate lastAllowed = today.plusDays(BookingPolicy.HORIZON_DAYS);
        LocalDate last = to.isAfter(lastAllowed) ? lastAllowed : to;

        Map<LocalDate, List<SlotResponse>> byDay = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }
        if (!first.isAfter(last)) {
            for (Slot slot : compute(request, first, last).slots()) {
                byDay.get(slot.date()).add(new SlotResponse(slot.time(), slot.startsAt()));
            }
        }
        return new AvailabilityResponse(request.zone().getId(), byDay.entrySet().stream()
                .map(e -> new DayAvailability(e.getKey(), e.getValue()))
                .toList());
    }

    /**
     * Comprueba que {@code startsAt} es exactamente uno de los huecos que ofreceríamos (así, todas las
     * reglas viven en un solo sitio) y devuelve los profesionales libres, el menos ocupado ese día primero.
     */
    BookableSlot findSlot(UUID serviceId, @Nullable UUID staffId, Instant startsAt) {
        Request request = load(serviceId, staffId);
        LocalDate date = LocalDate.ofInstant(startsAt, request.zone());
        LocalDate today = LocalDate.now(clock.withZone(request.zone()));
        if (date.isBefore(today) || date.isAfter(today.plusDays(BookingPolicy.HORIZON_DAYS))) {
            throw ApiException.conflict(SLOT_UNAVAILABLE);
        }
        Computed computed = compute(request, date, date);
        Slot slot = computed.slots().stream()
                .filter(s -> s.startsAt().equals(startsAt))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict(SLOT_UNAVAILABLE));

        // «Cualquier profesional»: repartimos la carga. Empate → el orden de la lista (por nombre).
        Map<UUID, Long> load = computed.appointments().stream()
                .filter(a -> LocalDate.ofInstant(a.getStartsAt(), request.zone()).equals(date))
                .collect(Collectors.groupingBy(Appointment::getStaffId, Collectors.counting()));
        Map<UUID, StaffMember> byId = request.staff().stream()
                .collect(Collectors.toMap(StaffMember::getId, Function.identity()));
        List<StaffMember> free = slot.staffIds().stream()
                .sorted(Comparator.comparingLong(id -> load.getOrDefault(id, 0L)))
                .map(byId::get)
                .toList();
        return new BookableSlot(request.service(), request.zone(), startsAt, free);
    }

    private record Request(ZoneId zone, Offering service, List<StaffMember> staff) {
    }

    private record Computed(List<Slot> slots, List<Appointment> appointments) {
    }

    /** El servicio (activo) y sus profesionales activos; un id de otro negocio o inactivo da 404. */
    private Request load(UUID serviceId, @Nullable UUID staffId) {
        Business business = businesses.findById(TenantContext.requireBusinessId())
                .orElseThrow(() -> ApiException.notFound("No existe ese negocio."));
        Offering service = offerings.findById(serviceId)
                .filter(Offering::isActive)
                .orElseThrow(() -> ApiException.notFound("No existe ese servicio."));
        List<StaffMember> staff = staffService.activeWithServices().stream()
                .filter(s -> s.serviceIds().contains(serviceId))
                .map(StaffService.ActiveStaff::member)
                .filter(m -> staffId == null || m.getId().equals(staffId))
                .toList();
        if (staffId != null && staff.isEmpty()) {
            throw ApiException.notFound("Ese profesional no existe o no hace este servicio.");
        }
        return new Request(ZoneId.of(business.getTimezone()), service, staff);
    }

    private Computed compute(Request request, LocalDate first, LocalDate last) {
        if (request.staff().isEmpty()) {
            return new Computed(List.of(), List.of());
        }
        ZoneId zone = request.zone();
        List<UUID> staffIds = request.staff().stream().map(StaffMember::getId).toList();

        Map<UUID, List<Segment>> weekly = hours.findByStaffIdIn(staffIds).stream()
                .collect(Collectors.groupingBy(WorkingHours::getStaffId, Collectors.mapping(
                        h -> new Segment(DayOfWeek.of(h.getDayOfWeek()), h.getStartTime(), h.getEndTime()),
                        Collectors.toList())));

        Set<LocalDate> businessClosed = new HashSet<>();
        Map<UUID, Set<LocalDate>> daysOff = new LinkedHashMap<>();
        for (Closure c : closures.findOverlapping(first, last)) {
            Set<LocalDate> target = c.getStaffId() == null
                    ? businessClosed
                    : daysOff.computeIfAbsent(c.getStaffId(), _ -> new HashSet<>());
            for (LocalDate d = c.getStartDate(); !d.isAfter(c.getEndDate()); d = d.plusDays(1)) {
                if (!d.isBefore(first) && !d.isAfter(last)) {
                    target.add(d);
                }
            }
        }

        // Límites del periodo en UTC desde la zona del negocio: un día de cambio de hora dura 23 o 25 h.
        Instant from = first.atStartOfDay(zone).toInstant();
        Instant to = last.plusDays(1).atStartOfDay(zone).toInstant();
        List<Appointment> blocking = appointments.findBlocking(staffIds, from, to);
        Map<UUID, List<Busy>> busy = blocking.stream()
                .collect(Collectors.groupingBy(Appointment::getStaffId, Collectors.mapping(
                        a -> new Busy(a.getStartsAt(), a.getBlockedUntil()), Collectors.toList())));

        List<StaffCalendar> calendars = request.staff().stream()
                .map(s -> new StaffCalendar(s.getId(), weekly.getOrDefault(s.getId(), List.of()),
                        daysOff.getOrDefault(s.getId(), Set.of()), busy.getOrDefault(s.getId(), List.of())))
                .toList();
        Offering service = request.service();
        var query = new Query(zone, first, last,
                Duration.ofMinutes(service.getDurationMin()),
                Duration.ofMinutes(service.getBufferMin()),
                BookingPolicy.STEP, clock.instant().plus(BookingPolicy.MIN_NOTICE), businessClosed);
        return new Computed(AvailabilityCalculator.compute(query, calendars), blocking);
    }
}
