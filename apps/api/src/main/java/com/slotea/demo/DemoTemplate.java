package com.slotea.demo;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import com.slotea.shared.validation.BrandColor;

/**
 * Qué hay en una copia de la demo de un sector: el negocio, su catálogo, sus profesionales (con su
 * horario) y el tono de sus clientes. Son solo datos: {@link DemoSeeder} los convierte en una agenda.
 *
 * <p>El constructor comprueba la coherencia (un dueño, como mucho un empleado con cuenta, servicios
 * que existen…), así que una plantilla mal escrita falla al arrancar y no al crear una copia.
 */
record DemoTemplate(
        String businessName,
        String contactEmail,
        /** El color de marca de la copia: cada sector tiene el suyo. */
        String brandColor,
        List<Service> services,
        List<Staff> staff,
        /** Nombres de los clientes; se apellidan «Ejemplo» o «Prueba» para que se vea que son inventados. */
        List<String> customerFirstNames,
        /** Notas que aparecen en alguna cita (una de cada diez). */
        List<String> notes,
        /** Cada cuánto se prueba a colocar una cita cuando un hueco queda libre (en minutos). */
        int slotStepMin) {

    /** Con qué cuenta de la copia está enlazada la ficha de un profesional. */
    enum Account { OWNER, EMPLOYEE, NONE }

    record Service(String name, String description, int durationMin, int bufferMin, int priceCents) {
    }

    /** {@code services}: índices de {@link DemoTemplate#services}. */
    record Staff(String name, Account account, List<Integer> services, Map<DayOfWeek, List<Range>> hours) {

        Staff {
            services = List.copyOf(services);
            hours = Collections.unmodifiableMap(new EnumMap<>(hours));
        }

        Set<DayOfWeek> days() {
            return hours.keySet();
        }

        List<Range> hoursOn(DayOfWeek day) {
            return hours.getOrDefault(day, List.of());
        }
    }

    record Range(LocalTime start, LocalTime end) {

        Range {
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("Empty range " + start + "-" + end);
            }
        }

        /** {@code "09:30-14:00"}. */
        static Range parse(String text) {
            String[] parts = text.split("-");
            return new Range(LocalTime.parse(parts[0]), LocalTime.parse(parts[1]));
        }
    }

    DemoTemplate {
        services = List.copyOf(services);
        staff = List.copyOf(staff);
        customerFirstNames = List.copyOf(customerFirstNames);
        notes = List.copyOf(notes);
        validate(businessName, brandColor, services, staff, slotStepMin);
    }

    private static void validate(String businessName, String brandColor, List<Service> services, List<Staff> staff,
            int slotStepMin) {
        // Lo mismo que exige la API (y en minúsculas, como lo guarda): la copia no se salta las reglas.
        if (!BrandColor.Colors.isAcceptable(brandColor) || !brandColor.equals(brandColor.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(businessName + ": the brand color must be readable on white");
        }
        if (staff.stream().filter(s -> s.account() == Account.OWNER).count() != 1) {
            throw new IllegalArgumentException(businessName + ": exactly one staff member must be the owner");
        }
        if (staff.stream().filter(s -> s.account() == Account.EMPLOYEE).count() > 1) {
            throw new IllegalArgumentException(businessName + ": at most one employee account");
        }
        for (Staff s : staff) {
            if (s.services().isEmpty() || s.services().stream().anyMatch(i -> i < 0 || i >= services.size())) {
                throw new IllegalArgumentException(businessName + ": bad services for " + s.name());
            }
        }
        if (IntStream.range(0, services.size()).anyMatch(i -> staff.stream().noneMatch(s -> s.services().contains(i)))) {
            throw new IllegalArgumentException(businessName + ": every service needs someone who does it");
        }
        if (slotStepMin <= 0 || slotStepMin % 15 != 0) {
            throw new IllegalArgumentException(businessName + ": the slot step must be a multiple of 15");
        }
    }

    Staff owner() {
        return staff.stream().filter(s -> s.account() == Account.OWNER).findFirst().orElseThrow();
    }

    /** El profesional con cuenta de empleado. Un sector de una sola profesional (psicología) no lo tiene. */
    Optional<Staff> employee() {
        return staff.stream().filter(s -> s.account() == Account.EMPLOYEE).findFirst();
    }

    /** Horario semanal de un profesional: {@code new Week().on(MON_TO_FRI, "09:30-14:00", "16:30-20:00")}. */
    static final class Week {

        private final Map<DayOfWeek, List<Range>> hours = new EnumMap<>(DayOfWeek.class);

        Week on(Set<DayOfWeek> days, String... ranges) {
            for (DayOfWeek day : days) {
                List<Range> list = hours.computeIfAbsent(day, _ -> new ArrayList<>());
                for (String range : ranges) {
                    list.add(Range.parse(range));
                }
            }
            return this;
        }

        Map<DayOfWeek, List<Range>> build() {
            Map<DayOfWeek, List<Range>> copy = new EnumMap<>(DayOfWeek.class);
            hours.forEach((day, ranges) -> copy.put(day, List.copyOf(ranges)));
            return copy;
        }
    }
}
