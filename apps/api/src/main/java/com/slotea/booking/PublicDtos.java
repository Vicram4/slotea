package com.slotea.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Contrato de la página pública de reservas (sin sesión). Nada de aquí expone datos internos. */
public final class PublicDtos {

    private PublicDtos() {
    }

    /** Lo que se puede reservar en un negocio: servicios activos que hace algún profesional activo. */
    public record PublicBusinessResponse(String slug, String name,
            @Schema(example = "Europe/Madrid") String timezone,
            List<PublicService> services, List<PublicStaff> staff) {
    }

    public record PublicService(UUID id, String name, @Nullable String description, int durationMin,
            @Nullable Integer priceCents) {
    }

    /** Un profesional y los servicios (de la lista anterior) que hace. */
    public record PublicStaff(UUID id, String displayName, List<UUID> serviceIds) {
    }

    /** Huecos día a día. Incluye los días sin huecos (para pintarlos desactivados). */
    public record AvailabilityResponse(String timezone, List<DayAvailability> days) {
    }

    public record DayAvailability(LocalDate date, List<SlotResponse> slots) {
    }

    /** {@code time} es la hora local del negocio, para mostrarla; {@code startsAt}, lo que se envía al reservar. */
    public record SlotResponse(
            @Schema(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$", example = "10:15")
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            Instant startsAt) {
    }

    /** {@code staffId} omitido = «cualquier profesional»: lo asigna la API. */
    public record BookingRequest(
            @NotNull UUID serviceId,
            @Nullable UUID staffId,
            @NotNull Instant startsAt,
            @NotNull @Valid CustomerInput customer,
            @Size(max = 500) @Nullable String notes) {
    }

    public record CustomerInput(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @Pattern(regexp = "^\\+?[0-9][0-9 ]{5,18}$", message = "no es un teléfono válido")
            @Nullable String phone) {

        @Override
        public String toString() {
            return "CustomerInput[fullName=***, email=***]"; // datos personales: fuera de los logs
        }
    }

    /** Resumen para la pantalla de confirmación. */
    public record BookingResponse(UUID id, String serviceName, String staffName, Instant startsAt, Instant endsAt,
            LocalDate date,
            @Schema(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$", example = "10:15")
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            String timezone, String customerEmail) {
    }
}
