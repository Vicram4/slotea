-- Fase 10: color de marca de cada negocio. La página pública lo usa en botones y selecciones.
-- Siempre hay uno (los negocios que ya existen se quedan con el neutro) y se guarda en minúsculas.
-- El contraste mínimo con el blanco (WCAG AA, 4,5:1) lo comprueba la API: SQL no lo calcula bien.
ALTER TABLE business ADD COLUMN brand_color text NOT NULL DEFAULT '#18181b'
    CONSTRAINT business_brand_color_check CHECK (brand_color ~ '^#[0-9a-f]{6}$');
