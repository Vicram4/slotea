package com.slotea.schedule;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Calcula los huecos libres para un servicio. Es una función pura (sin BD, sin Spring, sin reloj):
 * recibe todo lo que necesita y se prueba en milisegundos.
 *
 * <p>Para cada día local del negocio y cada profesional que trabaja ese día, propone inicios cada
 * {@code step} desde el comienzo de cada tramo. Un inicio vale si:
 * <ul>
 * <li>el servicio termina dentro del tramo (el margen sí puede pasarse: es tiempo de recoger);</li>
 * <li>{@code [inicio, fin + margen)} no pisa ninguna cita (mismo criterio que la restricción de la BD);</li>
 * <li>no es anterior a {@code notBefore} (ahora + antelación mínima).</li>
 * </ul>
 *
 * <p><strong>Cambio de hora.</strong> Los horarios son hora local y las citas, instantes. Se trabaja
 * en la zona del negocio y las duraciones son tiempo real:
 * <ul>
 * <li>Las horas que no existen (en Madrid, de 02:00 a 03:00 el último domingo de marzo) no se ofrecen.</li>
 * <li>Las que se repiten (de 02:00 a 03:00 el último domingo de octubre) se ofrecen una sola vez, con
 * el primer desfase (el de verano): dos botones «02:30» confundirían al cliente.</li>
 * </ul>
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    /** Un tramo del horario semanal, en hora local. */
    public record Segment(DayOfWeek day, LocalTime start, LocalTime end) {
    }

    /** Tiempo ocupado {@code [start, end)} (una cita, hasta su {@code blockedUntil}). */
    public record Busy(Instant start, Instant end) {

        boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && end.isAfter(otherStart);
        }
    }

    /** Todo lo que hace falta saber de un profesional. */
    public record StaffCalendar(UUID staffId, List<Segment> weeklyHours, Set<LocalDate> daysOff, List<Busy> busy) {
    }

    /**
     * Qué se busca. {@code from} y {@code to} son días locales (ambos incluidos); {@code notBefore} es
     * el primer inicio permitido (ahora + antelación mínima); {@code businessClosed}, los días en que
     * cierra todo el negocio.
     */
    public record Query(ZoneId zone, LocalDate from, LocalDate to, Duration duration, Duration buffer,
            Duration step, Instant notBefore, Set<LocalDate> businessClosed) {
    }

    /** Un hueco: hora local, el instante exacto y los profesionales libres (en el orden recibido). */
    public record Slot(LocalDate date, LocalTime time, Instant startsAt, List<UUID> staffIds) {
    }

    public static List<Slot> compute(Query q, List<StaffCalendar> staff) {
        if (q.step().isZero() || q.step().isNegative()) {
            throw new IllegalArgumentException("step must be positive");
        }
        List<Slot> result = new ArrayList<>();
        for (LocalDate date = q.from(); !date.isAfter(q.to()); date = date.plusDays(1)) {
            if (q.businessClosed().contains(date)) {
                continue;
            }
            // Hora local → profesionales libres. TreeMap: los huecos salen ordenados por hora.
            Map<LocalTime, List<UUID>> free = new TreeMap<>();
            for (StaffCalendar calendar : staff) {
                if (calendar.daysOff().contains(date)) {
                    continue;
                }
                for (Segment segment : calendar.weeklyHours()) {
                    if (segment.day() == date.getDayOfWeek()) {
                        addFreeStarts(q, date, segment, calendar, free);
                    }
                }
            }
            for (var entry : free.entrySet()) {
                Instant startsAt = date.atTime(entry.getKey()).atZone(q.zone()).toInstant();
                result.add(new Slot(date, entry.getKey(), startsAt, List.copyOf(entry.getValue())));
            }
        }
        return result;
    }

    private static void addFreeStarts(Query q, LocalDate date, Segment segment, StaffCalendar calendar,
            Map<LocalTime, List<UUID>> free) {
        Instant segmentEnd = date.atTime(segment.end()).atZone(q.zone()).toInstant();
        long stepMinutes = q.step().toMinutes();
        int startMinute = segment.start().toSecondOfDay() / 60;
        int endMinute = segment.end().toSecondOfDay() / 60;
        // Se itera en minutos del día (no con LocalTime.plus) para no dar la vuelta a medianoche.
        for (long minute = startMinute; minute < endMinute; minute += stepMinutes) {
            LocalTime time = LocalTime.ofSecondOfDay(minute * 60);
            LocalDateTime local = date.atTime(time);
            if (q.zone().getRules().getValidOffsets(local).isEmpty()) {
                continue; // hora que no existe (adelanto de la hora)
            }
            // atZone elige el primer desfase si la hora se repite (retraso de la hora).
            Instant start = local.atZone(q.zone()).toInstant();
            Instant end = start.plus(q.duration());
            if (end.isAfter(segmentEnd)) {
                break; // los siguientes inicios terminarían aún más tarde
            }
            if (start.isBefore(q.notBefore())) {
                continue;
            }
            Instant blockedUntil = end.plus(q.buffer());
            boolean taken = calendar.busy().stream().anyMatch(b -> b.overlaps(start, blockedUntil));
            if (!taken) {
                List<UUID> ids = free.computeIfAbsent(time, _ -> new ArrayList<>());
                if (!ids.contains(calendar.staffId())) {
                    ids.add(calendar.staffId());
                }
            }
        }
    }
}
