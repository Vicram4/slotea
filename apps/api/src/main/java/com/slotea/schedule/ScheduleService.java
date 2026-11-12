package com.slotea.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotea.business.StaffService;
import com.slotea.schedule.ScheduleDtos.ClosureRequest;
import com.slotea.schedule.ScheduleDtos.ClosureResponse;
import com.slotea.schedule.ScheduleDtos.TimeSlot;
import com.slotea.schedule.ScheduleDtos.WeeklyHours;
import com.slotea.shared.web.ApiException;

/** Horarios y cierres del negocio de la petición. Ids de otro negocio → 404. */
@Service
@Transactional(readOnly = true)
class ScheduleService {

    /** Un cierre de más de un año seguramente es un error al escribir la fecha. */
    static final int MAX_CLOSURE_DAYS = 366;

    private final WorkingHoursRepository hours;
    private final ClosureRepository closures;
    private final StaffService staff;

    ScheduleService(WorkingHoursRepository hours, ClosureRepository closures, StaffService staff) {
        this.hours = hours;
        this.closures = closures;
        this.staff = staff;
    }

    WeeklyHours weeklyHours(UUID staffId) {
        staff.require(staffId);
        return new WeeklyHours(hours.findByStaffIdOrderByDayOfWeekAscStartTimeAsc(staffId).stream()
                .map(TimeSlot::from)
                .toList());
    }

    @Transactional
    WeeklyHours replaceWeeklyHours(UUID staffId, WeeklyHours request) {
        staff.require(staffId);
        WeeklySchedule.validate(request.slots());
        hours.deleteAll(hours.findByStaffIdOrderByDayOfWeekAscStartTimeAsc(staffId));
        // Hibernate ejecuta los INSERT antes que los DELETE al hacer flush: sin este flush, un tramo
        // nuevo chocaría con el antiguo en la restricción EXCLUDE.
        hours.flush();
        hours.saveAllAndFlush(request.slots().stream()
                .map(s -> new WorkingHours(staffId, s.dayOfWeek(), s.start(), s.end()))
                .toList());
        return weeklyHours(staffId);
    }

    List<ClosureResponse> closures(@Nullable UUID staffId, @Nullable LocalDate from, @Nullable LocalDate to) {
        // LocalDate.MIN/MAX no caben en el tipo date de PostgreSQL.
        LocalDate start = from == null ? LocalDate.of(1, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.of(9999, 12, 31) : to;
        List<Closure> found;
        if (staffId == null) {
            found = closures.findOverlapping(start, end);
        } else {
            staff.require(staffId);
            found = closures.findOverlappingForStaff(staffId, start, end);
        }
        return found.stream().map(ClosureResponse::from).toList();
    }

    @Transactional
    ClosureResponse createClosure(ClosureRequest r) {
        if (r.staffId() != null) {
            staff.require(r.staffId());
        }
        if (r.endDate().isBefore(r.startDate())) {
            throw ApiException.invalidField("endDate", "debe ser igual o posterior a la fecha de inicio");
        }
        if (r.startDate().plusDays(MAX_CLOSURE_DAYS).isBefore(r.endDate())) {
            throw ApiException.invalidField("endDate", "un cierre no puede durar más de un año");
        }
        String reason = r.reason() == null || r.reason().isBlank() ? null : r.reason().strip();
        var closure = new Closure(r.staffId(), r.startDate(), r.endDate(), reason, Instant.now());
        return ClosureResponse.from(closures.saveAndFlush(closure));
    }

    @Transactional
    void deleteClosure(UUID id) {
        Closure closure = closures.findById(id).orElseThrow(() -> ApiException.notFound("No existe ese cierre."));
        closures.delete(closure);
    }
}
