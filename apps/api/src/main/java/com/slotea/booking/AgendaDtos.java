package com.slotea.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** La agenda del panel. Fechas y horas locales en la zona del negocio; los instantes, en UTC. */
public final class AgendaDtos {

    private AgendaDtos() {
    }

    /**
     * Las citas que empiezan entre {@code from} y {@code to} (ambos incluidos), canceladas incluidas.
     * {@code staff}: las columnas de la agenda (los profesionales activos más los inactivos que tienen
     * citas en el periodo); para un empleado, solo él.
     */
    public record AgendaResponse(String timezone, LocalDate from, LocalDate to, List<AgendaStaff> staff,
            List<AgendaAppointment> appointments) {
    }

    public record AgendaStaff(UUID id, String displayName, boolean active) {
    }

    public record AgendaCustomer(String fullName, String email, @Nullable String phone) {

        @Override
        public String toString() {
            return "AgendaCustomer[***]"; // fuera de los logs
        }
    }

    /**
     * {@code canCancel}: confirmada y aún no ha empezado. {@code canRecordOutcome}: no cancelada y ya
     * ha empezado (se puede marcar como completada o no presentada, o deshacerlo).
     */
    public record AgendaAppointment(UUID id, AppointmentStatus status, UUID staffId, String staffName,
            UUID serviceId, String serviceName, AgendaCustomer customer, Instant startsAt, Instant endsAt,
            LocalDate date,
            @Schema(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$", example = "10:15")
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @Schema(type = "string", pattern = "^[0-2][0-9]:[0-5][0-9]$", example = "10:45")
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            @Nullable String notes, Instant createdAt, @Nullable Instant cancelledAt, boolean canCancel,
            boolean canRecordOutcome) {
    }

    /** {@code COMPLETED}, {@code NO_SHOW} o {@code CONFIRMED} (deshacer). Para cancelar hay otro endpoint. */
    public record StatusRequest(@NotNull AppointmentStatus status) {
    }
}
