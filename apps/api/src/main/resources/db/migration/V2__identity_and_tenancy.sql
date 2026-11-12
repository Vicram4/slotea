-- Fase 4: identidad (usuarios, refresh tokens) y tenancy (negocios y membresías).
-- Los UUID (v7) los genera la aplicación. Las fechas, siempre timestamptz (UTC).

-- El negocio ES el tenant: el resto de tablas de negocio colgarán de él con FKs compuestas.
CREATE TABLE business (
    id            uuid        PRIMARY KEY,
    slug          text        NOT NULL,
    name          text        NOT NULL,
    timezone      text        NOT NULL DEFAULT 'Europe/Madrid',
    contact_email text        NOT NULL,
    is_demo       boolean     NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT business_slug_key UNIQUE (slug),
    -- El slug es una URL pública (/barberia-paco): minúsculas, números y guiones sueltos.
    CONSTRAINT business_slug_format CHECK (
        slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(slug) BETWEEN 3 AND 50
    ),
    CONSTRAINT business_name_length CHECK (char_length(name) BETWEEN 1 AND 100)
);

-- Identidad global: un usuario puede pertenecer a varios negocios (o a ninguno, si es cliente).
CREATE TABLE app_user (
    id            uuid        PRIMARY KEY,
    email         text        NOT NULL,
    password_hash text        NOT NULL,
    full_name     text        NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    -- La app normaliza a minúsculas; la BD lo garantiza, así el UNIQUE no distingue mayúsculas.
    CONSTRAINT app_user_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT app_user_email_key UNIQUE (email),
    CONSTRAINT app_user_full_name_length CHECK (char_length(full_name) BETWEEN 1 AND 100)
);

-- Rol de un usuario dentro de un negocio. Es la primera tabla filtrada por @TenantId.
-- Igual que el resto de tablas de negocio: id propio + UNIQUE (business_id, id) para FKs compuestas.
CREATE TABLE membership (
    id          uuid        PRIMARY KEY,
    business_id uuid        NOT NULL REFERENCES business (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role        text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT membership_business_id_id_key UNIQUE (business_id, id),
    CONSTRAINT membership_business_user_key UNIQUE (business_id, user_id),
    CONSTRAINT membership_role_check CHECK (role IN ('OWNER', 'STAFF'))
);

-- "¿A qué negocios pertenezco?" se consulta en cada login y refresh.
CREATE INDEX membership_user_id_idx ON membership (user_id);

-- Refresh tokens rotatorios. Solo guardamos el SHA-256 del token, nunca el token.
-- Todos los tokens de una misma sesión comparten family_id: si se reutiliza uno ya
-- rotado (señal de robo), se revoca la familia entera.
CREATE TABLE refresh_token (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id   uuid        NOT NULL,
    token_hash  bytea       NOT NULL,
    -- Negocio activo de la sesión (NULL para usuarios sin membresía).
    business_id uuid        REFERENCES business (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid        REFERENCES refresh_token (id) ON DELETE SET NULL,
    CONSTRAINT refresh_token_hash_key UNIQUE (token_hash),
    CONSTRAINT refresh_token_hash_length CHECK (octet_length(token_hash) = 32)
);

CREATE INDEX refresh_token_family_id_idx ON refresh_token (family_id);
CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
