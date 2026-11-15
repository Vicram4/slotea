package com.slotea.booking;

import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.booking.PublicDtos.AvailabilityResponse;
import com.slotea.booking.PublicDtos.ManagedAppointmentResponse;
import com.slotea.booking.PublicDtos.RescheduleRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * El enlace del email: ver, cancelar o cambiar la cita sin cuenta. Va bajo el slug para que el tenant
 * lo fije {@link PublicTenantFilter}, como en el resto de la página pública.
 *
 * <p>Los cambios son {@code POST} (con CSRF): los antivirus del correo abren los enlaces de los
 * emails, y un {@code GET} nunca debe cancelar nada.
 */
@Tag(name = "public")
@RestController
@RequestMapping("/api/v1/public/businesses/{slug}/appointments/manage/{token}")
class ManageAppointmentController {

    private final ManageAppointmentService manage;

    ManageAppointmentController(ManageAppointmentService manage) {
        this.manage = manage;
    }

    @Operation(summary = "La cita del enlace. 404 si el enlace no es válido (o es de otro negocio)")
    @GetMapping
    ManagedAppointmentResponse get(@PathVariable String slug, @PathVariable String token) {
        return manage.get(token);
    }

    @Operation(summary = "Cancelar la cita (hasta 2 h antes). Repetirlo no hace nada")
    @PostMapping("/cancel")
    ManagedAppointmentResponse cancel(@PathVariable String slug, @PathVariable String token) {
        return manage.cancel(token);
    }

    @Operation(summary = "Huecos para mover la cita (mismo servicio, máx. 14 días). La propia cita no ocupa")
    @GetMapping("/availability")
    AvailabilityResponse availability(@PathVariable String slug, @PathVariable String token,
            @RequestParam @Nullable UUID staffId,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return manage.availability(token, staffId, from, to);
    }

    @Operation(summary = "Cambiar la hora (hasta 2 h antes). 409 si el hueco ya no está libre")
    @PostMapping("/reschedule")
    ManagedAppointmentResponse reschedule(@PathVariable String slug, @PathVariable String token,
            @Valid @RequestBody RescheduleRequest request) {
        return manage.reschedule(token, request);
    }
}
