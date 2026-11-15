-- Fase 9: demo en un clic. Cada visitante recibe su propia copia de un negocio ficticio (is_demo),
-- que se borra a las 24 h.

-- Los emails de un negocio demo se guardan, pero NUNCA se envían: sus clientes son ficticios (rebotes
-- que perjudican al remitente en Brevo) y nadie debe poder usar la demo para mandar correos a
-- direcciones ajenas. Se ven en la «bandeja de la demo» del panel. El envío solo reclama 'PENDING'.
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_status_check;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_status_check
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEMO'));

-- Lo que busca la limpieza (copias de más de 24 h) y el tope de copias vivas. Índice parcial: pequeño.
CREATE INDEX business_demo_created_at_idx ON business (created_at) WHERE is_demo;
