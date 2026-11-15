-- Fase 7: outbox de emails (patrón «transactional outbox»).
-- El email se guarda en la MISMA transacción que el cambio de la cita: si la reserva hace rollback,
-- no sale ningún email; si se confirma, el email queda pendiente aunque el SMTP esté caído.
-- Lo envía la API justo después del commit y, si falla, lo reintenta POST /internal/jobs/outbox.
CREATE TABLE email_outbox (
    id              uuid        PRIMARY KEY,
    business_id     uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    appointment_id  uuid,
    type            text        NOT NULL,
    to_email        text        NOT NULL,
    -- Datos para renderizar la plantilla al enviar. Nunca el token del enlace: se recalcula (HMAC).
    payload         jsonb       NOT NULL,
    status          text        NOT NULL DEFAULT 'PENDING',
    attempts        integer     NOT NULL DEFAULT 0,
    -- Cuándo se puede (re)intentar. Al reclamar un email se adelanta unos minutos (lease): si la
    -- instancia muere a mitad del envío, otra ejecución lo recoge cuando vence.
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    CONSTRAINT email_outbox_business_id_id_key UNIQUE (business_id, id),
    -- FK compuesta, como el resto: un email no puede apuntar a la cita de otro negocio.
    CONSTRAINT email_outbox_appointment_fk FOREIGN KEY (business_id, appointment_id)
        REFERENCES appointment (business_id, id) ON DELETE CASCADE,
    CONSTRAINT email_outbox_type_check
        CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_RESCHEDULED', 'BOOKING_CANCELLED')),
    CONSTRAINT email_outbox_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT email_outbox_attempts_check CHECK (attempts >= 0),
    CONSTRAINT email_outbox_sent_at_check CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT email_outbox_to_email_length CHECK (char_length(to_email) BETWEEN 3 AND 254),
    CONSTRAINT email_outbox_last_error_length CHECK (char_length(last_error) <= 500)
);

-- Lo que busca el job: pendientes cuyo turno ya ha llegado (índice parcial, pequeño).
CREATE INDEX email_outbox_due_idx ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
-- Emails de una cita (bandeja de la demo, fase 9) y borrado en cascada.
CREATE INDEX email_outbox_appointment_idx ON email_outbox (business_id, appointment_id);
