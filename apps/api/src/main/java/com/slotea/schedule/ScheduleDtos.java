package com.slotea.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    /** Un tramo del horario, en hora local del negocio ({@code "09:30"}). */
    public record TimeSlot(
            @Schema(description = "1 = lunes … 7 = domingo") @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @Schema(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$", example = "09:30")
            @JsonFormat(pattern = "HH:mm") @NotNull LocalTime start,
            @Schema(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$", example = "14:00")
            @JsonFormat(pattern = "HH:mm") @NotNull LocalTime end) {

        static TimeSlot from(WorkingHours h) {
            return new TimeSlot(h.getDayOfWeek(), h.getStartTime(), h.getEndTime());
        }
    }

    /** El horario semanal completo de un profesional (se sustituye entero). */
    public record WeeklyHours(@NotNull @Size(max = 70) List<@NotNull @Valid TimeSlot> slots) {
    }

    public record ClosureResponse(UUID id, @Nullable UUID staffId, LocalDate startDate, LocalDate endDate,
            @Nullable String reason, Instant createdAt) {

        static ClosureResponse from(Closure c) {
            return new ClosureResponse(c.getId(), c.getStaffId(), c.getStartDate(), c.getEndDate(), c.getReason(),
                    c.getCreatedAt());
        }
    }

    /** {@code staffId} omitido = cierra todo el negocio. Fechas inclusivas. */
    public record ClosureRequest(
            @Nullable UUID staffId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 200) @Nullable String reason) {
    }
}
