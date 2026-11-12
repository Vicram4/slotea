package com.slotea.schedule;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.schedule.ScheduleDtos.ClosureRequest;
import com.slotea.schedule.ScheduleDtos.ClosureResponse;
import com.slotea.schedule.ScheduleDtos.WeeklyHours;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Horario semanal de cada profesional y cierres. STAFF los ve; solo OWNER los cambia. */
@Tag(name = "schedule")
@RestController
@RequestMapping("/api/v1")
class ScheduleController {

    private final ScheduleService schedule;

    ScheduleController(ScheduleService schedule) {
        this.schedule = schedule;
    }

    @Operation(summary = "Horario semanal de un profesional (hora local del negocio)")
    @GetMapping("/staff/{staffId}/working-hours")
    @PreAuthorize("hasRole('STAFF')")
    WeeklyHours weeklyHours(@PathVariable UUID staffId) {
        return schedule.weeklyHours(staffId);
    }

    @Operation(summary = "Sustituir el horario semanal de un profesional (solo el dueño)")
    @PutMapping("/staff/{staffId}/working-hours")
    @PreAuthorize("hasRole('OWNER')")
    WeeklyHours replaceWeeklyHours(@PathVariable UUID staffId, @Valid @RequestBody WeeklyHours request) {
        return schedule.replaceWeeklyHours(staffId, request);
    }

    @Operation(summary = "Cierres del negocio y ausencias de profesionales que tocan el periodo [from, to]")
    @GetMapping("/closures")
    @PreAuthorize("hasRole('STAFF')")
    List<ClosureResponse> closures(@RequestParam @Nullable UUID staffId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) @Nullable LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) @Nullable LocalDate to) {
        return schedule.closures(staffId, from, to);
    }

    @Operation(summary = "Crear un cierre del negocio o una ausencia de un profesional (solo el dueño)")
    @PostMapping("/closures")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<ClosureResponse> createClosure(@Valid @RequestBody ClosureRequest request) {
        ClosureResponse created = schedule.createClosure(request);
        return ResponseEntity.created(URI.create("/api/v1/closures/" + created.id())).body(created);
    }

    @Operation(summary = "Borrar un cierre (solo el dueño)")
    @DeleteMapping("/closures/{id}")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<Void> deleteClosure(@PathVariable UUID id) {
        schedule.deleteClosure(id);
        return ResponseEntity.noContent().build();
    }
}
