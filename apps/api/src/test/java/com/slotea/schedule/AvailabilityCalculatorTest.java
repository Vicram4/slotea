package com.slotea.schedule;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SUNDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.slotea.schedule.AvailabilityCalculator.Busy;
import com.slotea.schedule.AvailabilityCalculator.Query;
import com.slotea.schedule.AvailabilityCalculator.Segment;
import com.slotea.schedule.AvailabilityCalculator.Slot;
import com.slotea.schedule.AvailabilityCalculator.StaffCalendar;

class AvailabilityCalculatorTest {

    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    static final LocalDate MONDAY_IN_WINTER = LocalDate.of(2027, 1, 11);
    /** En Madrid, a las 02:00 pasan a ser las 03:00 (el día dura 23 h). */
    static final LocalDate SPRING_FORWARD = LocalDate.of(2027, 3, 28);
    /** En Madrid, a las 03:00 vuelven a ser las 02:00 (el día dura 25 h). */
    static final LocalDate FALL_BACK = LocalDate.of(2027, 10, 31);

    static final UUID ANA = UUID.randomUUID();
    static final UUID LUIS = UUID.randomUUID();

    @Test
    void offersStartsEveryStepWhileTheServiceFitsInTheSegment() {
        var slots = compute(query(MONDAY_IN_WINTER, 30, 0), calendar(ANA, MONDAY, "09:00", "10:00"));

        assertThat(times(slots)).containsExactly("09:00", "09:15", "09:30");
        // Invierno en Madrid: UTC+1.
        assertThat(slots.getFirst().startsAt()).isEqualTo(Instant.parse("2027-01-11T08:00:00Z"));
    }

    @Test
    void splitShiftOffersBothSegments() {
        var ana = new StaffCalendar(ANA, List.of(
                new Segment(MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new Segment(MONDAY, LocalTime.of(16, 0), LocalTime.of(17, 0))), Set.of(), List.of());

        assertThat(times(compute(query(MONDAY_IN_WINTER, 60, 0), ana))).containsExactly("09:00", "16:00");
    }

    @Test
    void theBufferMayRunPastClosingButTheServiceMayNot() {
        var slots = compute(query(MONDAY_IN_WINTER, 30, 15), calendar(ANA, MONDAY, "09:00", "10:00"));

        assertThat(times(slots)).containsExactly("09:00", "09:15", "09:30");
    }

    @Test
    void existingAppointmentsBlockUntilTheirBufferEnds() {
        // Cita de 10:00 a 10:30 (+10 de margen → ocupado hasta 10:40). Nuestro servicio: 30 + 10.
        var ana = calendar(ANA, MONDAY, "09:00", "12:00", busy("10:00", "10:40"));

        var slots = compute(query(MONDAY_IN_WINTER, 30, 10), ana);

        // 09:15 acaba a las 09:45 y bloquea hasta las 09:55: cabe. 09:30 bloquearía hasta las 10:10: no.
        // 10:30 se solaparía con el margen de la cita (hasta 10:40). 10:45 ya está libre.
        assertThat(times(slots)).startsWith("09:00", "09:15", "10:45").doesNotContain("09:30", "10:00", "10:30");
    }

    @Test
    void backToBackAppointmentsAreAllowed() {
        // '[)': acabar a las 10:00 cuando otra cita empieza a las 10:00, y empezar cuando otra acaba.
        var ana = calendar(ANA, MONDAY, "09:00", "11:00", busy("10:00", "10:30"));

        var slots = compute(query(MONDAY_IN_WINTER, 30, 0), ana);

        assertThat(times(slots)).containsExactly("09:00", "09:15", "09:30", "10:30");
    }

    @Test
    void businessClosuresAndStaffAbsencesRemoveTheDay() {
        var ana = calendar(ANA, MONDAY, "09:00", "10:00");
        var luisOff = new StaffCalendar(LUIS, ana.weeklyHours(), Set.of(MONDAY_IN_WINTER), List.of());

        var withAbsence = compute(query(MONDAY_IN_WINTER, 60, 0), ana, luisOff);
        assertThat(withAbsence).singleElement().extracting(Slot::staffIds).isEqualTo(List.of(ANA));

        var closed = new Query(MADRID, MONDAY_IN_WINTER, MONDAY_IN_WINTER, Duration.ofMinutes(60), Duration.ZERO,
                Duration.ofMinutes(15), Instant.EPOCH, Set.of(MONDAY_IN_WINTER));
        assertThat(AvailabilityCalculator.compute(closed, List.of(ana))).isEmpty();
    }

    @Test
    void startsBeforeNotBeforeAreNotOffered() {
        Instant nineTwentyLocal = Instant.parse("2027-01-11T08:20:00Z");
        var q = new Query(MADRID, MONDAY_IN_WINTER, MONDAY_IN_WINTER, Duration.ofMinutes(30), Duration.ZERO,
                Duration.ofMinutes(15), nineTwentyLocal, Set.of());

        assertThat(times(AvailabilityCalculator.compute(q, List.of(calendar(ANA, MONDAY, "09:00", "10:00")))))
                .containsExactly("09:30");
    }

    @Test
    void eachSlotListsEveryFreeStaffInTheGivenOrder() {
        var luis = calendar(LUIS, MONDAY, "09:00", "10:00", busy("09:00", "09:30"));
        var ana = calendar(ANA, MONDAY, "09:00", "10:00");

        var slots = compute(query(MONDAY_IN_WINTER, 30, 0), luis, ana);

        assertThat(slots).extracting(Slot::staffIds)
                .containsExactly(List.of(ANA), List.of(ANA), List.of(LUIS, ANA));
    }

    @Test
    void theSameLocalTimeIsADifferentInstantInSummerAndWinter() {
        LocalDate fridayBefore = SPRING_FORWARD.minusDays(2);
        LocalDate mondayAfter = SPRING_FORWARD.plusDays(1);
        var ana = new StaffCalendar(ANA, List.of(
                new Segment(DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)),
                new Segment(MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))), Set.of(), List.of());
        var q = new Query(MADRID, fridayBefore, mondayAfter, Duration.ofMinutes(60), Duration.ZERO,
                Duration.ofMinutes(15), Instant.EPOCH, Set.of());

        var slots = AvailabilityCalculator.compute(q, List.of(ana));

        assertThat(slots).extracting(Slot::startsAt).containsExactly(
                Instant.parse("2027-03-26T09:00:00Z"), // 10:00 en invierno (UTC+1)
                Instant.parse("2027-03-29T08:00:00Z")); // 10:00 en verano (UTC+2)
    }

    @Test
    void springForwardSkipsTheMissingHourAndUsesRealDurations() {
        var slots = compute(query(SPRING_FORWARD, 60, 0), calendar(ANA, SUNDAY, "01:00", "04:00"));

        // De 02:00 a 02:59 no existen. 01:45 + 60 min reales = 03:45, que aún cabe antes de las 04:00.
        assertThat(times(slots)).containsExactly("01:00", "01:15", "01:30", "01:45", "03:00");
        assertThat(slots.getFirst().startsAt()).isEqualTo(Instant.parse("2027-03-28T00:00:00Z"));
        assertThat(slots.getLast().startsAt()).isEqualTo(Instant.parse("2027-03-28T01:00:00Z"));
    }

    @Test
    void fallBackOffersRepeatedTimesOnceWithTheSummerOffset() {
        var slots = compute(query(FALL_BACK, 60, 0), calendar(ANA, SUNDAY, "01:00", "04:00"));

        // Cada hora local una sola vez (sin dos «02:30»). 03:15 acabaría a las 04:15: no cabe.
        assertThat(times(slots)).containsExactly(
                "01:00", "01:15", "01:30", "01:45", "02:00", "02:15", "02:30", "02:45", "03:00");
        Slot twoThirty = slots.get(6);
        assertThat(twoThirty.startsAt()).isEqualTo(Instant.parse("2027-10-31T00:30:00Z")); // UTC+2
        assertThat(slots.getLast().startsAt()).isEqualTo(Instant.parse("2027-10-31T02:00:00Z")); // UTC+1
    }

    @Test
    void usesTheBusinessTimeZoneNotMadrid() {
        // El 15-03-2027 Nueva York ya está en horario de verano (UTC-4) y Madrid aún no.
        LocalDate monday = LocalDate.of(2027, 3, 15);
        var q = new Query(NEW_YORK, monday, monday, Duration.ofMinutes(60), Duration.ZERO, Duration.ofMinutes(15),
                Instant.EPOCH, Set.of());

        var slots = AvailabilityCalculator.compute(q, List.of(calendar(ANA, MONDAY, "10:00", "11:00")));

        assertThat(slots).singleElement().extracting(Slot::startsAt).isEqualTo(Instant.parse("2027-03-15T14:00:00Z"));
    }

    @Test
    void doesNotWrapAroundMidnight() {
        var slots = compute(query(MONDAY_IN_WINTER, 30, 0), calendar(ANA, MONDAY, "23:00", "23:55"));

        assertThat(times(slots)).containsExactly("23:00", "23:15");
    }

    // --- helpers ---

    static Query query(LocalDate day, int durationMin, int bufferMin) {
        return new Query(MADRID, day, day, Duration.ofMinutes(durationMin), Duration.ofMinutes(bufferMin),
                Duration.ofMinutes(15), Instant.EPOCH, Set.of());
    }

    static StaffCalendar calendar(UUID id, DayOfWeek day, String start, String end, Busy... busy) {
        return new StaffCalendar(id, List.of(new Segment(day, LocalTime.parse(start), LocalTime.parse(end))),
                Set.of(), List.of(busy));
    }

    /** Ocupado entre dos horas locales del lunes de invierno de los tests. */
    static Busy busy(String start, String end) {
        return new Busy(MONDAY_IN_WINTER.atTime(LocalTime.parse(start)).atZone(MADRID).toInstant(),
                MONDAY_IN_WINTER.atTime(LocalTime.parse(end)).atZone(MADRID).toInstant());
    }

    static List<Slot> compute(Query q, StaffCalendar... staff) {
        return AvailabilityCalculator.compute(q, List.of(staff));
    }

    static List<String> times(List<Slot> slots) {
        return slots.stream().map(s -> s.time().toString()).toList();
    }
}
