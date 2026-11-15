package com.slotea.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.validation.constraints.NotBlank;

/**
 * @param from remitente de todos los emails, p. ej. {@code Slotea <reservas@example.com>}
 *             ({@code MAIL_FROM}). Con Brevo tiene que ser un remitente verificado.
 */
@Validated
@ConfigurationProperties("app.mail")
record MailSettings(@NotBlank(message = "MAIL_FROM is not set (see .env.example)") String from) {

    MailSettings {
        // Vacío lo explica @NotBlank. Si no, falla al arrancar (y no en cada envío) si está mal escrita.
        if (from != null && !from.isBlank()) {
            try {
                new InternetAddress(from, true).validate();
            } catch (AddressException e) {
                throw new IllegalStateException("MAIL_FROM is not a valid address: " + e.getMessage(), e);
            }
        }
    }
}
