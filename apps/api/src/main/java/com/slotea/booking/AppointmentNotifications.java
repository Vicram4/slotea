package com.slotea.booking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import com.slotea.booking.Customers.Contact;
import com.slotea.business.Business;
import com.slotea.notification.AppointmentEmail;
import com.slotea.notification.EmailOutbox;
import com.slotea.notification.EmailType;

/**
 * Los emails de un cambio en una cita: el del cliente y, si el cambio lo ha hecho el cliente, el
 * aviso al negocio (a su email de contacto). Ambos con los mismos datos y en la misma transacción.
 *
 * <p>Los negocios de demostración ({@code is_demo}) no reciben avisos: su email de contacto es
 * ficticio y cada rebote perjudica la reputación del remitente.
 */
@Component
class AppointmentNotifications {

    private final EmailOutbox outbox;

    AppointmentNotifications(EmailOutbox outbox) {
        this.outbox = outbox;
    }

    /** Lo que cambia y quién recibe qué. {@code toBusiness} = {@code null}: solo el cliente. */
    record Change(EmailType toCustomer, @Nullable EmailType toBusiness, @Nullable Instant previousStartsAt) {
    }

    /**
     * Encola los emails dentro de la transacción actual y devuelve sus ids, para {@link #sendNow}
     * cuando se confirme.
     */
    List<UUID> enqueue(Change change, Business business, Appointment appointment, Contact customer,
            String serviceName, String staffName) {
        var email = new AppointmentEmail(appointment.getId(), business.getSlug(), business.getName(),
                business.getContactEmail(), business.getTimezone(), customer.fullName(), serviceName, staffName,
                appointment.getStartsAt(), appointment.getEndsAt(), change.previousStartsAt(), customer.email(),
                customer.phone());
        List<UUID> ids = new ArrayList<>(2);
        ids.add(outbox.enqueue(change.toCustomer(), customer.email(), email));
        if (change.toBusiness() != null && !business.isDemo()) {
            ids.add(outbox.enqueue(change.toBusiness(), business.getContactEmail(), email));
        }
        return ids;
    }

    /** Después del commit. Primero el del cliente: es el que espera la persona que acaba de hacer clic. */
    void sendNow(List<UUID> emailIds) {
        emailIds.forEach(outbox::sendNow);
    }
}
