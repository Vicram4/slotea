package com.slotea.notification;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Lo que necesita un email sobre una cita, tal y como estaba al encolarlo. Se guarda como
 * {@code payload} en la outbox: así se puede renderizar (o reintentar) sin volver a la BD.
 *
 * <p>No lleva el token del enlace: se recalcula al enviar a partir de {@code appointmentId}.
 *
 * @param previousStartsAt solo en los cambios de hora: la hora anterior.
 * @param customerEmail    para los avisos al negocio (responder al cliente). {@code null} en los
 *                         emails encolados antes de la fase 8.
 * @param customerPhone    opcional; solo se muestra en los avisos al negocio.
 */
public record AppointmentEmail(
        UUID appointmentId,
        String businessSlug,
        String businessName,
        String businessEmail,
        String timezone,
        String customerName,
        String serviceName,
        String staffName,
        Instant startsAt,
        Instant endsAt,
        @Nullable Instant previousStartsAt,
        @Nullable String customerEmail,
        @Nullable String customerPhone) {

    @Override
    public String toString() {
        return "AppointmentEmail[appointmentId=" + appointmentId + ", customer=***]"; // fuera de los logs
    }
}
