-- Fase 5: catálogo del negocio (profesionales, servicios, horarios y cierres).
-- Patrón de todas las tablas de negocio: business_id + UNIQUE (business_id, id) y FKs COMPUESTAS
-- (business_id, x_id). Así, por esquema, nada de un negocio puede apuntar a algo de otro.
-- Horas y fechas en hora LOCAL del negocio: «abre a las 9:00» no cambia con el cambio de hora.
-- La conversión a timestamptz se hace al calcular los huecos (fase 6).

-- Membership ya tiene UNIQUE (business_id, user_id): sirve de destino para una FK compuesta.

-- Profesional: la persona que atiende. Puede estar enlazado a un usuario del panel (o no).
CREATE TABLE staff_member (
    id           uuid        PRIMARY KEY,
    business_id  uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id      uuid,
    display_name text        NOT NULL,
    active       boolean     NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT staff_member_business_id_id_key UNIQUE (business_id, id),
    -- Un usuario tiene como mucho una ficha de profesional por negocio.
    CONSTRAINT staff_member_business_user_key UNIQUE (business_id, user_id),
    -- Solo se puede enlazar a un miembro del MISMO negocio. Si pierde la membresía, la ficha
    -- se queda (tiene historial) pero se desenlaza: SET NULL solo de user_id (PostgreSQL 15+).
    CONSTRAINT staff_member_membership_fk FOREIGN KEY (business_id, user_id)
        REFERENCES membership (business_id, user_id) ON DELETE SET NULL (user_id),
    CONSTRAINT staff_member_display_name_length CHECK (char_length(display_name) BETWEEN 1 AND 100)
);

-- Servicio que se puede reservar. price_cents solo se muestra (no hay pagos); en euros.
CREATE TABLE service (
    id           uuid        PRIMARY KEY,
    business_id  uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    name         text        NOT NULL,
    description  text,
    duration_min integer     NOT NULL,
    buffer_min   integer     NOT NULL DEFAULT 0,
    price_cents  integer,
    active       boolean     NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT service_business_id_id_key UNIQUE (business_id, id),
    CONSTRAINT service_name_length CHECK (char_length(name) BETWEEN 1 AND 100),
    CONSTRAINT service_description_length CHECK (char_length(description) <= 500),
    -- Huecos de 5 en 5 minutos: la agenda se calcula sobre esa rejilla.
    CONSTRAINT service_duration_check CHECK (duration_min BETWEEN 5 AND 480 AND duration_min % 5 = 0),
    CONSTRAINT service_buffer_check CHECK (buffer_min BETWEEN 0 AND 120 AND buffer_min % 5 = 0),
    CONSTRAINT service_price_check CHECK (price_cents >= 0)
);

-- Nombre único por negocio sin distinguir mayúsculas («Corte» = «corte»).
CREATE UNIQUE INDEX service_business_name_key ON service (business_id, lower(name));

-- Qué servicios hace cada profesional. Las dos FKs comparten business_id: no se pueden mezclar negocios.
CREATE TABLE staff_service (
    business_id uuid NOT NULL,
    staff_id    uuid NOT NULL,
    service_id  uuid NOT NULL,
    CONSTRAINT staff_service_pkey PRIMARY KEY (business_id, staff_id, service_id),
    CONSTRAINT staff_service_staff_fk FOREIGN KEY (business_id, staff_id)
        REFERENCES staff_member (business_id, id) ON DELETE CASCADE,
    CONSTRAINT staff_service_service_fk FOREIGN KEY (business_id, service_id)
        REFERENCES service (business_id, id) ON DELETE CASCADE
);

CREATE INDEX staff_service_service_idx ON staff_service (business_id, service_id);

-- Rango de horas del día, para poder usar EXCLUDE con solapamientos (no existe de serie).
CREATE TYPE timerange AS RANGE (subtype = time);

-- Horario semanal de cada profesional. Varios tramos por día = jornada partida.
CREATE TABLE working_hours (
    id          uuid    PRIMARY KEY,
    business_id uuid    NOT NULL,
    staff_id    uuid    NOT NULL,
    day_of_week integer NOT NULL,
    start_time  time    NOT NULL,
    end_time    time    NOT NULL,
    CONSTRAINT working_hours_business_id_id_key UNIQUE (business_id, id),
    CONSTRAINT working_hours_staff_fk FOREIGN KEY (business_id, staff_id)
        REFERENCES staff_member (business_id, id) ON DELETE CASCADE,
    -- ISO-8601: 1 = lunes … 7 = domingo (igual que java.time.DayOfWeek).
    CONSTRAINT working_hours_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT working_hours_order_check CHECK (start_time < end_time),
    -- Mismo patrón que la doble reserva: la BD impide tramos solapados del mismo día.
    -- '[)': 10:00-14:00 y 14:00-18:00 son contiguos, no se solapan.
    CONSTRAINT working_hours_no_overlap EXCLUDE USING gist (
        business_id WITH =,
        staff_id WITH =,
        day_of_week WITH =,
        timerange(start_time, end_time, '[)') WITH &&
    )
);

-- Días sin servicio: staff_id NULL = cierra todo el negocio; con valor = ausencia de un profesional.
-- Fechas inclusivas en hora local. En consultas: daterange(start_date, end_date, '[]').
CREATE TABLE closure (
    id          uuid        PRIMARY KEY,
    business_id uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    staff_id    uuid,
    start_date  date        NOT NULL,
    end_date    date        NOT NULL,
    reason      text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT closure_business_id_id_key UNIQUE (business_id, id),
    -- MATCH SIMPLE: con staff_id NULL no se comprueba (cierre de todo el negocio).
    CONSTRAINT closure_staff_fk FOREIGN KEY (business_id, staff_id)
        REFERENCES staff_member (business_id, id) ON DELETE CASCADE,
    CONSTRAINT closure_dates_check CHECK (start_date <= end_date),
    CONSTRAINT closure_reason_length CHECK (char_length(reason) <= 200)
);

CREATE INDEX closure_business_dates_idx ON closure (business_id, start_date, end_date);
