package com.slotea.booking;

/** Las canceladas no ocupan hueco (la restricción {@code appointment_no_overlap} las ignora). */
public enum AppointmentStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
}
