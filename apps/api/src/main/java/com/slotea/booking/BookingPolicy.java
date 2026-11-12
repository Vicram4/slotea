package com.slotea.booking;

import java.time.Duration;

/** Reglas fijas de la reserva pública (iguales para todos los negocios en la v1). */
final class BookingPolicy {

    /** Cada cuánto se ofrece un inicio. Los servicios van de 5 en 5 min, pero 15 da listas legibles. */
    static final Duration STEP = Duration.ofMinutes(15);

    /** Nadie puede reservar para dentro de 5 minutos: el negocio no se enteraría a tiempo. */
    static final Duration MIN_NOTICE = Duration.ofMinutes(30);

    /** Hasta cuántos días vista se puede reservar (hoy + 60). */
    static final int HORIZON_DAYS = 60;

    /** Días por consulta de disponibilidad: la web pide de semana en semana. */
    static final int MAX_RANGE_DAYS = 14;

    private BookingPolicy() {
    }
}
