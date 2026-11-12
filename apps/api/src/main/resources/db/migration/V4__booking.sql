-- Fase 6: clientes y citas. La garantía contra la doble reserva vive AQUÍ, no en Java.
-- Mismo patrón que el catálogo: business_id + UNIQUE (business_id, id) y FKs compuestas.

-- Cliente de un negocio. Por ahora solo invitados (email sin verificar, sin cuenta).
-- El email identifica al cliente dentro de cada negocio; el mismo email en otro negocio es otra ficha.
CREATE TABLE customer (
    id          uuid        PRIMARY KEY,
    business_id uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    email       text        NOT NULL,
    full_name   text        NOT NULL,
    phone       text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT customer_business_id_id_key UNIQUE (business_id, id),
    CONSTRAINT customer_business_email_key UNIQUE (business_id, email),
    CONSTRAINT customer_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT customer_full_name_length CHECK (char_length(full_name) BETWEEN 1 AND 100),
    CONSTRAINT customer_phone_length CHECK (char_length(phone) BETWEEN 6 AND 20)
);

-- Una cita. Las horas en UTC (timestamptz); el cálculo de huecos se hace en la zona del negocio.
-- blocked_until = fin + margen del servicio: el profesional no está libre hasta entonces.
CREATE TABLE appointment (
    id                uuid        PRIMARY KEY,
    business_id       uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    staff_id          uuid        NOT NULL,
    service_id        uuid        NOT NULL,
    customer_id       uuid        NOT NULL,
    starts_at         timestamptz NOT NULL,
    ends_at           timestamptz NOT NULL,
    blocked_until     timestamptz NOT NULL,
    status            text        NOT NULL DEFAULT 'CONFIRMED',
    -- SHA-256 del token del enlace para gestionar la cita sin cuenta (el token nunca se guarda).
    manage_token_hash bytea       NOT NULL,
    notes             text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    cancelled_at      timestamptz,
    version           integer     NOT NULL DEFAULT 0,
    CONSTRAINT appointment_business_id_id_key UNIQUE (business_id, id),
    CONSTRAINT appointment_manage_token_hash_key UNIQUE (manage_token_hash),
    -- FKs compuestas: una cita no puede mezclar profesional, servicio o cliente de otro negocio.
    -- Sin ON DELETE: profesionales y servicios se desactivan, no se borran (las citas son historial).
    -- Es NO ACTION (no RESTRICT) a propósito: se comprueba al final de la sentencia, así que borrar
    -- el negocio entero (cascada a citas y a profesionales a la vez) sí funciona.
    CONSTRAINT appointment_staff_fk FOREIGN KEY (business_id, staff_id)
        REFERENCES staff_member (business_id, id),
    CONSTRAINT appointment_service_fk FOREIGN KEY (business_id, service_id)
        REFERENCES service (business_id, id),
    CONSTRAINT appointment_customer_fk FOREIGN KEY (business_id, customer_id)
        REFERENCES customer (business_id, id),
    CONSTRAINT appointment_status_check
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT appointment_times_check CHECK (starts_at < ends_at AND ends_at <= blocked_until),
    -- Una cita cancelada sabe cuándo se canceló, y solo ella.
    CONSTRAINT appointment_cancelled_at_check CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    CONSTRAINT appointment_token_length CHECK (octet_length(manage_token_hash) = 32),
    CONSTRAINT appointment_notes_length CHECK (char_length(notes) <= 500),
    -- LA regla: un profesional no puede tener dos citas que se solapen, en ningún momento.
    -- · Cualquier solape, no solo «la misma hora» (10:00-10:30 contra 10:15-10:45).
    -- · '[)': se puede reservar a las 10:30 justo cuando acaba la cita de las 10:00.
    -- · Las canceladas no cuentan: liberan el hueco al instante.
    -- · PostgreSQL la comprueba de forma atómica, aunque lleguen dos peticiones a la vez desde
    --   procesos o servidores distintos. Violación = SQLState 23P01 → la API responde 409.
    CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
        business_id WITH =,
        staff_id WITH =,
        tstzrange(starts_at, blocked_until, '[)') WITH &&
    ) WHERE (status <> 'CANCELLED')
);

-- Agenda del negocio por fechas (fase 8) y citas de un cliente.
CREATE INDEX appointment_business_starts_at_idx ON appointment (business_id, starts_at);
CREATE INDEX appointment_customer_idx ON appointment (business_id, customer_id);
CREATE INDEX appointment_service_idx ON appointment (business_id, service_id);
