package com.slotea.notification;

/** Emails que manda la API. Si añades uno, añádelo también al {@code CHECK} de {@code email_outbox}. */
public enum EmailType {
    // Al cliente.
    BOOKING_CONFIRMED,
    BOOKING_RESCHEDULED,
    BOOKING_CANCELLED,
    // Al negocio (a su email de contacto), cuando es el cliente quien hace el cambio.
    BUSINESS_NEW_BOOKING,
    BUSINESS_BOOKING_RESCHEDULED,
    BUSINESS_BOOKING_CANCELLED;

    /** El destinatario es el negocio: si responde, le contesta al cliente. */
    public boolean toBusiness() {
        return name().startsWith("BUSINESS_");
    }
}
