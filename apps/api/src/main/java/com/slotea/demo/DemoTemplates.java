package com.slotea.demo;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.slotea.demo.DemoDtos.DemoSector;
import com.slotea.demo.DemoTemplate.Account;
import com.slotea.demo.DemoTemplate.Range;
import com.slotea.demo.DemoTemplate.Service;
import com.slotea.demo.DemoTemplate.Staff;
import com.slotea.demo.DemoTemplate.Week;

/**
 * El contenido de la demo de cada sector. Todo es ficticio: nombres que se ven inventados («Dueño
 * Demo», «Lucía Ejemplo»), emails {@code @example.com} (dominio reservado) y sin teléfonos.
 *
 * <p>Cada sector enseña algo distinto del producto: servicios largos que no caben en cualquier hueco
 * (peluquería, taller), un margen entre citas (psicología, veterinaria), profesionales que solo hacen
 * parte del catálogo o que trabajan en turnos distintos (fisioterapia).
 */
final class DemoTemplates {

    private DemoTemplates() {
    }

    static final String OWNER_NAME = "Dueño Demo";
    static final String EMPLOYEE_NAME = "Empleado Demo";
    static final String UNLINKED_NAME = "Profesional Demo";

    private static final Set<DayOfWeek> MON_TO_FRI = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> MON_TO_THU = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
    private static final Set<DayOfWeek> TUE_TO_FRI = EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> FRIDAY = EnumSet.of(DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> SATURDAY = EnumSet.of(DayOfWeek.SATURDAY);

    /** Nombres claramente inventados (todos se apellidan «Ejemplo» o «Prueba»). */
    private static final List<String> FIRST_NAMES = List.of(
            "Lucía", "Mario", "Sofía", "Hugo", "Martina", "Pablo", "Julia", "Daniel", "Paula", "Álvaro",
            "Carmen", "Diego", "Elena", "Javier", "Irene", "Sergio", "Laura", "Adrián", "Marta", "Raúl",
            "Nerea", "Iván", "Clara", "Óscar");

    static DemoTemplate of(DemoSector sector) {
        return switch (sector) {
            case BARBERSHOP -> BARBERSHOP;
            case HAIR_AND_BEAUTY -> HAIR_AND_BEAUTY;
            case MECHANIC -> MECHANIC;
            case PSYCHOLOGY -> PSYCHOLOGY;
            case PHYSIOTHERAPY -> PHYSIOTHERAPY;
            case VETERINARY -> VETERINARY;
        };
    }

    // --- Barbería: la demo original --------------------------------------------------------------

    static final DemoTemplate BARBERSHOP = new DemoTemplate(
            "Barbería Demo Slotea",
            "barberia-demo@example.com",
            "#1e3a8a", // azul marino
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
    private static Map<DayOfWeek, List<Range>> barbershopHours(Set<DayOfWeek> weekdays) {
        return new Week().on(weekdays, "09:30-14:00", "16:30-20:00").on(SATURDAY, "10:00-14:00").build();
    }

    // --- Peluquería y estética: servicios de 2 h y una esteticista con su propio catálogo --------

    static final DemoTemplate HAIR_AND_BEAUTY = new DemoTemplate(
            "Peluquería y Estética Demo",
            "peluqueria-demo@example.com",
            "#be185d", // frambuesa
            List.of(
                    new Service("Corte de mujer", "Lavado, corte y secado.", 45, 0, 2200),
                    new Service("Corte de hombre", "Lavado y corte.", 30, 0, 1400),
                    new Service("Tinte", "Color de raíz a puntas, con lavado y secado.", 120, 0, 4500),
                    new Service("Mechas", "Mechas o balayage, con matiz y secado.", 150, 0, 6500),
                    new Service("Lavado y peinado", "Para un evento o para el día a día.", 30, 0, 1500),
                    new Service("Manicura", "Limado, cutículas y esmalte semipermanente.", 45, 0, 1800),
                    new Service("Depilación de piernas", "Piernas completas con cera tibia.", 30, 0, 2000),
                    new Service("Limpieza facial", "Limpieza profunda e hidratación.", 60, 0, 3500)),
            List.of(
                    new Staff(OWNER_NAME, Account.OWNER, List.of(0, 1, 2, 3, 4), salonHours()),
                    new Staff(EMPLOYEE_NAME, Account.EMPLOYEE, List.of(0, 1, 2, 4), salonHours()),
                    new Staff(UNLINKED_NAME, Account.NONE, List.of(5, 6, 7), salonHours())),
            FIRST_NAMES,
            List.of("Primera visita.", "Trae una foto del color que quiere.", "Piel sensible: prueba de tolerancia hecha.",
                    "Prefiere esmalte en tonos nude."),
            30);

    /** Cierra el lunes (como muchas peluquerías) y abre el sábado por la mañana. */
    private static Map<DayOfWeek, List<Range>> salonHours() {
        return new Week().on(TUE_TO_FRI, "10:00-14:00", "16:00-20:00").on(SATURDAY, "09:00-14:00").build();
    }

    // --- Taller mecánico: citas de horas y margen para mover el coche ----------------------------

    private static final Map<DayOfWeek, List<Range>> WORKSHOP_HOURS =
            new Week().on(MON_TO_FRI, "08:00-13:00", "15:00-18:00").build();

    static final DemoTemplate MECHANIC = new DemoTemplate(
            "Taller Demo Slotea",
            "taller-demo@example.com",
            "#c2410c", // naranja óxido
            List.of(
                    new Service("Cambio de aceite y filtros", "Aceite, filtro de aceite y de aire.", 45, 15, 8900),
                    new Service("Revisión completa", "La revisión del fabricante, con sello en el libro.", 120, 0, 18000),
                    new Service("Pastillas de freno", "Cambio de pastillas del eje delantero.", 90, 0, 12000),
                    new Service("Diagnosis electrónica", "Lectura de averías con el equipo de diagnosis.", 45, 0, 4500),
                    new Service("Pre-ITV", "Revisamos lo que mira la ITV antes de que vayas.", 30, 0, 3000),
                    new Service("Montaje de neumáticos", "Montaje y equilibrado de cuatro ruedas.", 60, 0, 2500)),
            List.of(
                    new Staff(OWNER_NAME, Account.OWNER, List.of(0, 1, 2, 3, 4, 5), WORKSHOP_HOURS),
                    new Staff(EMPLOYEE_NAME, Account.EMPLOYEE, List.of(0, 1, 2, 4, 5), WORKSHOP_HOURS),
                    new Staff(UNLINKED_NAME, Account.NONE, List.of(1, 3, 4), WORKSHOP_HOURS)),
            FIRST_NAMES,
            List.of("Coche de ejemplo, gasolina, de 2015.", "Ruido al frenar.", "Testigo de motor encendido.",
                    "Deja el coche a primera hora y lo recoge por la tarde."),
            30);

    // --- Psicología: una sola profesional, sesiones de 50 min + 10 y siempre en punto ------------

    static final DemoTemplate PSYCHOLOGY = new DemoTemplate(
            "Psicología Demo Slotea",
            "psicologia-demo@example.com",
            "#0f766e", // verde azulado
            List.of(
                    new Service("Primera consulta", "Una primera entrevista para ver qué necesitas.", 50, 10, 5000),
                    new Service("Sesión individual", "Terapia individual para adultos.", 50, 10, 6000),
                    new Service("Terapia de pareja", "Sesión con los dos miembros de la pareja.", 50, 10, 7500)),
            List.of(new Staff(OWNER_NAME, Account.OWNER, List.of(0, 1, 2),
                    new Week().on(MON_TO_THU, "16:00-21:00").on(FRIDAY, "09:00-14:00").build())),
            FIRST_NAMES.subList(0, 14), // pocos pacientes, que repiten
            List.of("Prefiere las sesiones a última hora.", "Viene cada dos semanas.",
                    "Pide la factura a nombre de su empresa."),
            60);

    // --- Fisioterapia: tres fisios con turnos distintos ------------------------------------------

    static final DemoTemplate PHYSIOTHERAPY = new DemoTemplate(
            "Fisioterapia Demo Slotea",
            "fisioterapia-demo@example.com",
            "#6d28d9", // violeta
            List.of(
                    new Service("Valoración inicial", "Exploración y plan de tratamiento.", 60, 0, 4500),
                    new Service("Sesión de fisioterapia", "Tratamiento manual y ejercicios.", 45, 0, 4000),
                    new Service("Sesión larga", "Para tratar varias zonas en la misma cita.", 60, 0, 5000),
                    new Service("Punción seca", "Para puntos gatillo musculares.", 30, 0, 3500),
                    new Service("Drenaje linfático", "Drenaje manual.", 60, 0, 5000)),
            List.of(
                    new Staff(OWNER_NAME, Account.OWNER, List.of(0, 1, 2, 3, 4),
                            new Week().on(MON_TO_FRI, "09:00-14:00", "16:00-20:00").build()),
                    new Staff(EMPLOYEE_NAME, Account.EMPLOYEE, List.of(0, 1, 2, 3),
                            new Week().on(MON_TO_FRI, "08:00-15:00").build()),
                    new Staff(UNLINKED_NAME, Account.NONE, List.of(0, 1, 2, 4),
                            new Week().on(MON_TO_FRI, "15:00-21:00").build())),
            FIRST_NAMES,
            List.of("Lumbalgia: tercera sesión.", "Trae el informe de su médico.", "Prepara una carrera popular.",
                    "Esguince de tobillo."),
            30);

    // --- Veterinaria: margen para limpiar la consulta y peluquería canina aparte ------------------

    private static final Map<DayOfWeek, List<Range>> VET_HOURS =
            new Week().on(MON_TO_FRI, "10:00-14:00", "17:00-20:30").on(SATURDAY, "10:00-13:30").build();

    static final DemoTemplate VETERINARY = new DemoTemplate(
            "Clínica Veterinaria Demo",
            "veterinaria-demo@example.com",
            "#15803d", // verde
            List.of(
                    new Service("Consulta general", "Exploración y diagnóstico.", 20, 10, 3500),
                    new Service("Vacunación", "Vacuna y actualización de la cartilla.", 15, 5, 3000),
                    new Service("Revisión anual", "Chequeo completo de tu mascota.", 30, 0, 4000),
                    new Service("Desparasitación", "Interna y externa.", 15, 5, 2000),
                    new Service("Baño y corte", "Peluquería canina: baño, secado y corte.", 90, 0, 3500),
                    new Service("Corte de uñas", "Rápido y sin estrés.", 15, 0, 1000)),
            List.of(
                    new Staff(OWNER_NAME, Account.OWNER, List.of(0, 1, 2, 3, 5), VET_HOURS),
                    new Staff(EMPLOYEE_NAME, Account.EMPLOYEE, List.of(0, 1, 2, 3), VET_HOURS),
                    new Staff(UNLINKED_NAME, Account.NONE, List.of(4, 5),
                            new Week().on(TUE_TO_FRI, "10:00-14:00", "17:00-20:00").on(SATURDAY, "10:00-13:30")
                                    .build())),
            FIRST_NAMES,
            List.of("Perro, 6 años: se pone nervioso en la sala de espera.", "Gata, 2 años.",
                    "Conejo: primera visita.", "Trae la cartilla de vacunación."),
            30);
}
