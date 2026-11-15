package com.slotea.notification;

/** Emails que manda la API. Si añades uno, añádelo también al {@code CHECK} de {@code email_outbox}. */
public enum EmailType {
    BOOKING_CONFIRMED,
    BOOKING_RESCHEDULED,
    BOOKING_CANCELLED
}
