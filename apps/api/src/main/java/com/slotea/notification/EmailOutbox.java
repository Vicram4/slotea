package com.slotea.notification;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.slotea.shared.tenancy.TenantContext;

import tools.jackson.databind.json.JsonMapper;

/**
 * Punto de entrada para mandar emails (patrón <em>transactional outbox</em>), en dos pasos:
 * <ol>
 * <li>{@link #enqueue} <strong>dentro</strong> de la transacción que cambia la cita: el email se
 *     guarda o se descarta junto con ese cambio. Nunca sale un email de una reserva que ha hecho
 *     rollback, ni se pierde el de una que se ha confirmado.</li>
 * <li>{@link #sendNow} <strong>después</strong> del commit, para que el cliente lo reciba al momento.
 *     Si falla, no pasa nada: el email sigue pendiente y lo reintenta el job.</li>
 * </ol>
 *
 * <p>¿Por qué no un {@code @TransactionalEventListener(AFTER_COMMIT)}? Porque en ese momento la
 * transacción aún no ha soltado su conexión: el envío ocuparía una segunda conexión por petición (el
 * pool de producción es de 5). ¿Y por qué no en segundo plano? Porque Cloud Run quita la CPU en
 * cuanto se envía la respuesta: el envío podría quedarse congelado.
 *
 * <p>Va con SQL (como {@code Customers}): {@code business_id} se pone a mano desde el tenant actual.
 */
@Component
public class EmailOutbox {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final EmailDispatcher dispatcher;
    private final Clock clock;

    EmailOutbox(JdbcClient jdbc, JsonMapper json, EmailDispatcher dispatcher, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    /** Guarda el email en la transacción actual (obligatoria) y devuelve su id, para {@link #sendNow}. */
    public UUID enqueue(EmailType type, String to, AppointmentEmail email) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Emails must be queued in the transaction that changes the data");
        }
        UUID id = UuidVersion7Strategy.INSTANCE.generateUuid(null);
        jdbc.sql("""
                INSERT INTO email_outbox (id, business_id, appointment_id, type, to_email, payload, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """)
                .params(id, TenantContext.requireBusinessId(), email.appointmentId(), type.name(), to,
                        json.writeValueAsString(email), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .update();
        return id;
    }

    /**
     * Intenta enviar ya un email encolado. Fuera de cualquier transacción (la del cambio ya tiene que
     * estar confirmada). Nunca lanza excepciones: un fallo del SMTP no debe estropear la respuesta.
     */
    public void sendNow(UUID emailId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Send after the transaction commits, not inside it");
        }
        dispatcher.deliverNow(emailId);
    }
}
