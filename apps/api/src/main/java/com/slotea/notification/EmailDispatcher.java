package com.slotea.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.notification.EmailRenderer.RenderedEmail;

import tools.jackson.databind.json.JsonMapper;

/**
 * Envía los emails de la outbox. Entrega <em>al menos una vez</em>: si el SMTP acepta el email pero
 * la instancia muere antes de marcarlo como enviado, se reintentará (y llegará duplicado). Es la
 * garantía habitual del patrón; la contraria (como mucho una vez) perdería emails.
 *
 * <p><strong>Reclamar antes de enviar.</strong> Una sentencia corta marca los emails como «en curso»
 * (suma un intento y aplaza {@code next_attempt_at} un {@link #LEASE}) y los devuelve. Usa
 * {@code FOR UPDATE SKIP LOCKED}: dos ejecuciones simultáneas del job (o el job y el envío inmediato)
 * se reparten los emails en vez de mandarlos dos veces. El envío por SMTP va <em>fuera</em> de la
 * transacción, para no tener una conexión a la BD abierta mientras se habla con el servidor de correo.
 * Si la instancia muere a mitad, el email vuelve a estar disponible cuando vence el lease.
 *
 * <p><strong>Reintentos.</strong> Tras un fallo espera {@link #BACKOFF} (1 min, 5 min, 30 min, 2 h);
 * tras {@link #MAX_ATTEMPTS} intentos lo da por perdido ({@code FAILED}).
 *
 * <p>Cruza negocios, pero no necesita {@code SystemTransactions}: solo usa SQL con {@code business_id}
 * explícito (el tenant de Hibernate no interviene) y el email ya trae todos sus datos.
 */
@Component
class EmailDispatcher {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LEASE = Duration.ofMinutes(2);
    /** Espera tras el intento n (1, 2, 3, 4) antes del siguiente. */
    static final List<Duration> BACKOFF = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));
    private static final int BATCH = 20;

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final EmailRenderer renderer;
    private final JavaMailSender mail;
    private final MailSettings settings;
    private final TransactionTemplate tx;
    private final Clock clock;

    EmailDispatcher(JdbcClient jdbc, JsonMapper json, EmailRenderer renderer, JavaMailSender mail,
            MailSettings settings, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.renderer = renderer;
        this.mail = mail;
        this.settings = settings;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Resultado de una pasada del job. */
    record DeliveryReport(int sent, int retrying, int failed) {

        DeliveryReport plus(Outcome outcome) {
            return switch (outcome) {
                case SENT -> new DeliveryReport(sent + 1, retrying, failed);
                case RETRYING -> new DeliveryReport(sent, retrying + 1, failed);
                case FAILED -> new DeliveryReport(sent, retrying, failed + 1);
            };
        }
    }

    enum Outcome { SENT, RETRYING, FAILED }

    private record Claimed(UUID id, EmailType type, String to, String payload, int attempts) {
    }

    /** Envío inmediato tras el commit. Si otro ya lo ha reclamado (o no existe), no hace nada. */
    void deliverNow(UUID emailId) {
        try {
            claim(emailId, 1).forEach(this::deliver);
        } catch (RuntimeException e) {
            // Ni siquiera hemos podido reclamarlo (¿BD caída?): sigue pendiente para el job.
            log.warn("Could not send email {} right away; the job will retry it", emailId, e);
        }
    }

    /** Lo que llama el job: todos los emails pendientes cuyo turno ha llegado, por lotes, hasta {@code max}. */
    DeliveryReport deliverDue(int max) {
        var report = new DeliveryReport(0, 0, 0);
        int processed = 0;
        while (processed < max) {
            List<Claimed> batch = claim(null, Math.min(BATCH, max - processed));
            if (batch.isEmpty()) {
                break;
            }
            for (Claimed email : batch) {
                report = report.plus(deliver(email));
            }
            processed += batch.size();
        }
        return report;
    }

    /** Reclama hasta {@code limit} emails pendientes y vencidos (o solo {@code onlyId}). */
    private List<Claimed> claim(@Nullable UUID onlyId, int limit) {
        Instant now = clock.instant();
        return tx.execute(_ -> jdbc.sql("""
                UPDATE email_outbox o
                SET attempts = o.attempts + 1, next_attempt_at = :leaseUntil
                FROM (SELECT id FROM email_outbox
                      WHERE status = 'PENDING' AND next_attempt_at <= :now
                        AND (CAST(:onlyId AS uuid) IS NULL OR id = CAST(:onlyId AS uuid))
                      ORDER BY next_attempt_at
                      LIMIT :limit
                      FOR UPDATE SKIP LOCKED) due
                WHERE o.id = due.id
                RETURNING o.id, o.type, o.to_email, CAST(o.payload AS text) AS payload, o.attempts
                """)
                .param("leaseUntil", utc(now.plus(LEASE)))
                .param("now", utc(now))
                .param("onlyId", onlyId)
                .param("limit", limit)
                .query((rs, _) -> new Claimed(rs.getObject("id", UUID.class),
                        EmailType.valueOf(rs.getString("type")), rs.getString("to_email"),
                        rs.getString("payload"), rs.getInt("attempts")))
                .list());
    }

    private Outcome deliver(Claimed email) {
        try {
            AppointmentEmail data = json.readValue(email.payload(), AppointmentEmail.class);
            send(email.to(), data.businessEmail(), renderer.render(email.type(), data));
        } catch (RuntimeException e) {
            return failed(email, e);
        }
        tx.executeWithoutResult(_ -> jdbc.sql("""
                UPDATE email_outbox SET status = 'SENT', sent_at = ?, last_error = NULL WHERE id = ?
                """)
                .params(utc(clock.instant()), email.id())
                .update());
        return Outcome.SENT;
    }

    private void send(String to, String replyTo, RenderedEmail email) {
        mail.send(message -> {
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(settings.from());
            helper.setTo(to);
            // Si el cliente responde, le contesta el negocio (no la plataforma).
            helper.setReplyTo(replyTo);
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
        });
    }

    private Outcome failed(Claimed email, RuntimeException e) {
        boolean giveUp = email.attempts() >= MAX_ATTEMPTS;
        Instant next = clock.instant().plus(giveUp ? Duration.ZERO : BACKOFF.get(email.attempts() - 1));
        // Ni el payload ni el cuerpo del email: solo el tipo de error y su mensaje (del SMTP o de la plantilla).
        String error = truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 500);
        tx.executeWithoutResult(_ -> jdbc.sql("""
                UPDATE email_outbox SET status = ?, next_attempt_at = ?, last_error = ? WHERE id = ?
                """)
                .params(giveUp ? "FAILED" : "PENDING", utc(next), error, email.id())
                .update());
        if (giveUp) {
            log.error("Email {} failed {} times; giving up", email.id(), email.attempts(), e);
            return Outcome.FAILED;
        }
        log.warn("Email {} failed (attempt {}); retrying at {}", email.id(), email.attempts(), next, e);
        return Outcome.RETRYING;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
