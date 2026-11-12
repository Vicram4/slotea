package com.slotea.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.slotea.schedule.ScheduleDtos.TimeSlot;
import com.slotea.shared.web.ApiException;

class WeeklyScheduleTest {

    static TimeSlot slot(int day, String start, String end) {
        return new TimeSlot(day, LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void acceptsSplitShiftsContiguousSlotsAndAnyOrder() {
        assertThatCode(() -> WeeklySchedule.validate(List.of(
                slot(3, "16:00", "20:00"),
                slot(1, "14:00", "18:00"),
                slot(1, "09:00", "14:00"), // contiguo con el de las 14:00
                slot(3, "09:00", "13:30"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> WeeklySchedule.validate(List.of())).doesNotThrowAnyException();
    }

    @Test
    void rejectsOverlapsPointingToTheLaterSlot() {
        assertField(List.of(slot(1, "13:00", "18:00"), slot(2, "09:00", "18:00"), slot(1, "09:00", "13:05")),
                "slots[0].start", "se solapa con otro tramo del mismo día");
        // Un tramo contenido dentro de otro también se solapa.
        assertField(List.of(slot(5, "09:00", "20:00"), slot(5, "12:00", "13:00")),
                "slots[1].start", "se solapa con otro tramo del mismo día");
    }

    @Test
    void rejectsEmptyOrReversedSlots() {
        assertField(List.of(slot(1, "10:00", "10:00")), "slots[0].end", "debe ser posterior al inicio");
        assertField(List.of(slot(1, "09:00", "10:00"), slot(2, "18:00", "09:00")),
                "slots[1].end", "debe ser posterior al inicio");
    }

    @Test
    void rejectsTimesOutsideTheFiveMinuteGrid() {
        assertField(List.of(slot(1, "09:02", "10:00")), "slots[0].start", "usa intervalos de 5 minutos");
        assertField(List.of(slot(1, "09:00", "10:00:30")), "slots[0].end", "usa intervalos de 5 minutos");
    }

    private static void assertField(List<TimeSlot> slots, String field, String message) {
        assertThatThrownBy(() -> WeeklySchedule.validate(slots))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(400);
                    assertThat(ex.getBody().getProperties()).containsEntry("errors",
                            List.of(Map.of("field", field, "message", message)));
                });
    }
}
