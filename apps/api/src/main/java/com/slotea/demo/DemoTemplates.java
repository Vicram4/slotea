package com.slotea.demo;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.slotea.demo.DemoTemplate.Account;
import com.slotea.demo.DemoTemplate.Service;
import com.slotea.demo.DemoTemplate.Staff;
import com.slotea.demo.DemoTemplate.Week;

/**
 * El contenido de la demo de cada sector. Todo es ficticio: nombres que se ven inventados («Dueño
 * Demo», «Lucía Ejemplo»), emails {@code @example.com} (dominio reservado) y sin teléfonos.
 */
final class DemoTemplates {

    private DemoTemplates() {
    }

    static final String OWNER_NAME = "Dueño Demo";
    static final String EMPLOYEE_NAME = "Empleado Demo";
    static final String UNLINKED_NAME = "Profesional Demo";

    private static final Set<DayOfWeek> MON_TO_FRI = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> TUE_TO_FRI = EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> SATURDAY = EnumSet.of(DayOfWeek.SATURDAY);

    /** Nombres claramente inventados (todos se apellidan «Ejemplo» o «Prueba»). */
    private static final List<String> FIRST_NAMES = List.of(
            "Lucía", "Mario", "Sofía", "Hugo", "Martina", "Pablo", "Julia", "Daniel", "Paula", "Álvaro",
            "Carmen", "Diego", "Elena", "Javier", "Irene", "Sergio", "Laura", "Adrián", "Marta", "Raúl",
            "Nerea", "Iván", "Clara", "Óscar");

    static final DemoTemplate BARBERSHOP = new DemoTemplate(
            "Barbería Demo Slotea",
            "barberia-demo@example.com",
            List.of(
                    new Service("Corte de pelo", "Lavado, corte y peinado.", 30, 0, 1500),
                    new Service("Arreglo de barba", "Perfilado con navaja y toalla caliente.", 20, 0, 1000),
                    new Service("Corte y barba", "El corte de pelo y el arreglo de barba en una sola cita.", 45, 5, 2200),
                    new Service("Corte infantil", "Para menores de 12 años.", 20, 0, 1000)),
            List.of(
                    new Staff(OWNER_NAME, Account.OWNER, List.of(0, 1, 2, 3), barbershopHours(MON_TO_FRI)),
                    new Staff(EMPLOYEE_NAME, Account.EMPLOYEE, List.of(0, 1, 2), barbershopHours(MON_TO_FRI)),
                    new Staff(UNLINKED_NAME, Account.NONE, List.of(0, 1, 3), barbershopHours(TUE_TO_FRI))),
            FIRST_NAMES,
            List.of("Primera visita.", "Prefiere degradado bajo.", "Viene con su hijo.",
                    "Pregunta por productos para la barba."),
            30);

    /** Jornada partida entre semana; el sábado, solo por la mañana. El domingo cierra. */
    private static java.util.Map<DayOfWeek, List<DemoTemplate.Range>> barbershopHours(Set<DayOfWeek> weekdays) {
        return new Week().on(weekdays, "09:30-14:00", "16:30-20:00").on(SATURDAY, "10:00-14:00").build();
    }
}
