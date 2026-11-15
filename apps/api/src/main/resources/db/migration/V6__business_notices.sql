-- Fase 8: avisos al negocio. Cuando un cliente reserva, cambia o cancela una cita, el negocio recibe
-- un email (en su contact_email) por la misma outbox. Solo cambian los tipos permitidos.
ALTER TABLE email_outbox DROP CONSTRAINT email_outbox_type_check;
ALTER TABLE email_outbox ADD CONSTRAINT email_outbox_type_check
    CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_RESCHEDULED', 'BOOKING_CANCELLED',
                    'BUSINESS_NEW_BOOKING', 'BUSINESS_BOOKING_RESCHEDULED', 'BUSINESS_BOOKING_CANCELLED'));
