package com.slotea.booking;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.booking.AgendaDtos.AgendaAppointment;
import com.slotea.booking.AgendaDtos.AgendaResponse;
import com.slotea.booking.AgendaDtos.StatusRequest;
import com.slotea.shared.security.Viewer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Las citas del negocio, para el panel. El dueño ve todas; un empleado, solo las suyas (lo decide el
 * servidor a partir de su sesión: ver {@link AgendaService}).
 */
@Tag(name = "agenda")
@RestController
@RequestMapping("/api/v1/appointments")
class AgendaController {

    private final AgendaService agenda;

    AgendaController(AgendaService agenda) {
        this.agenda = agenda;
    }

    @Operation(summary = "Citas que empiezan entre dos fechas (incluidas, máx. 42 días, en la zona del negocio). "
            + "Un empleado solo recibe las suyas")
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    AgendaResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
            @RequestParam @Nullable UUID staffId) {
        return agenda.agenda(Viewer.of(jwt), from, to, staffId);
    }

    @Operation(summary = "Una cita (404 si es de otro profesional y quien pregunta es un empleado)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    AgendaAppointment get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return agenda.get(Viewer.of(jwt), id);
    }

    @Operation(summary = "Marcar cómo acabó una cita que ya ha empezado: COMPLETED, NO_SHOW o CONFIRMED (deshacer)")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('STAFF')")
    AgendaAppointment recordOutcome(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody StatusRequest request) {
        return agenda.recordOutcome(Viewer.of(jwt), id, request.status());
    }

    @Operation(summary = "Cancelar una cita que aún no ha empezado y avisar al cliente por email. "
            + "Repetirlo no hace nada")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('STAFF')")
    AgendaAppointment cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return agenda.cancel(Viewer.of(jwt), id);
    }
}
