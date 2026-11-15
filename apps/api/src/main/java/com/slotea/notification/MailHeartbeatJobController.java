package com.slotea.notification;

import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.shared.web.ApiException;

import io.swagger.v3.oas.annotations.Hidden;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * «Latido» del email: manda un correo real al propio remitente ({@code MAIL_FROM}). Lo llama el cron
 * externo una vez por semana ({@code jobs.yml}) con la cabecera {@code X-Jobs-Token}.
 *
 * <p><strong>Por qué existe.</strong> Brevo desactiva una clave SMTP tras 90 días sin uso, y los emails
 * de la demo no se envían nunca (estado {@code DEMO}): sin negocios reales, la clave caducaría sin que
 * nadie se enterase. De paso vigila el envío de verdad: si el SMTP no acepta el email, responde 503,
 * el workflow falla y GitHub avisa por email.
 *
 * <p><strong>Por qué no pasa por la outbox.</strong> Cada email de la outbox pertenece a un negocio
 * (tenant) y su envío puede esperar al reintento; aquí no hay negocio y el resultado tiene que saberse
 * en la propia petición. El destinatario es siempre el remitente de la plataforma, nunca una dirección
 * que llegue de fuera: no abre ningún camino para mandar correos a terceros.
 */
@Hidden
@RestController
class MailHeartbeatJobController {

    private static final Logger log = LoggerFactory.getLogger(MailHeartbeatJobController.class);

    private final JavaMailSender mail;
    private final MailSettings settings;
    private final Clock clock;

    MailHeartbeatJobController(JavaMailSender mail, MailSettings settings, Clock clock) {
        this.mail = mail;
        this.settings = settings;
        this.clock = clock;
    }

    @PostMapping("/internal/jobs/mail-heartbeat")
    Map<String, String> heartbeat() {
        try {
            mail.send(message -> {
                var helper = new MimeMessageHelper(message, false, "UTF-8");
                helper.setFrom(settings.from());
                helper.setTo(platformAddress());
                helper.setSubject("Slotea · comprobación semanal del email");
                helper.setText("""
                        Este email lo envía cada semana el job /internal/jobs/mail-heartbeat (%s).

                        Sirve para que el proveedor SMTP no desactive la clave por falta de uso y para \
                        comprobar que el envío funciona. No hace falta responder.
                        """.formatted(clock.instant()));
            });
        } catch (MailException e) {
            log.error("Mail heartbeat failed: the SMTP server did not accept the email", e);
            throw ApiException.serviceUnavailable("El servidor de correo no ha aceptado el email de comprobación.");
        }
        return Map.of("status", "sent");
    }

    /** Solo la dirección de {@code MAIL_FROM}, sin el nombre visible. */
    private String platformAddress() {
        try {
            return new InternetAddress(settings.from(), true).getAddress();
        } catch (AddressException e) {
            throw new IllegalStateException("MAIL_FROM is validated at startup", e);
        }
    }
}
