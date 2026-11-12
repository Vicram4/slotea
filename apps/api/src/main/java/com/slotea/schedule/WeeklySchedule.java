package com.slotea.schedule;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import com.slotea.schedule.ScheduleDtos.TimeSlot;
import com.slotea.shared.web.ApiException;

/**
 * Reglas del horario semanal que miran varios tramos a la vez. La BD garantiza lo mismo
 * ({@code CHECK} y {@code EXCLUDE}); aquí se valida antes para devolver un 400 con el campo exacto.
 */
final class WeeklySchedule {

    /** Las citas se encajan en una rejilla de 5 minutos. */
    static final int STEP_MINUTES = 5;

    private WeeklySchedule() {
    }

    /** @throws ApiException 400 con {@code errors[].field} = {@code slots[i].start|end} */
    static void validate(List<TimeSlot> slots) {
        for (int i = 0; i < slots.size(); i++) {
            TimeSlot slot = slots.get(i);
            if (!onGrid(slot.start().getMinute(), slot.start().getSecond(), slot.start().getNano())) {
                throw ApiException.invalidField("slots[%d].start".formatted(i), "usa intervalos de 5 minutos");
            }
            if (!onGrid(slot.end().getMinute(), slot.end().getSecond(), slot.end().getNano())) {
                throw ApiException.invalidField("slots[%d].end".formatted(i), "usa intervalos de 5 minutos");
            }
            if (!slot.start().isBefore(slot.end())) {
                throw ApiException.invalidField("slots[%d].end".formatted(i), "debe ser posterior al inicio");
            }
        }
        // Ordenados por día y hora, cada tramo debe empezar cuando (o después de que) acabe el anterior.
        List<Integer> order = IntStream.range(0, slots.size()).boxed()
                .sorted(Comparator.comparing((Integer i) -> slots.get(i).dayOfWeek())
                        .thenComparing(i -> slots.get(i).start()))
                .toList();
        for (int k = 1; k < order.size(); k++) {
            TimeSlot previous = slots.get(order.get(k - 1));
            TimeSlot current = slots.get(order.get(k));
            if (previous.dayOfWeek().equals(current.dayOfWeek()) && current.start().isBefore(previous.end())) {
                throw ApiException.invalidField("slots[%d].start".formatted(order.get(k)),
                        "se solapa con otro tramo del mismo día");
            }
        }
    }

    private static boolean onGrid(int minute, int second, int nano) {
        return minute % STEP_MINUTES == 0 && second == 0 && nano == 0;
    }
}
