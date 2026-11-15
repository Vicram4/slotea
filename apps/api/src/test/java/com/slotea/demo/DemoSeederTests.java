package com.slotea.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.demo.DemoSeeder.SeededDemo;

/**
 * El seed crea una agenda creíble a cualquier hora: dentro del horario (en hora de Madrid, también la
 * semana del cambio de hora), nada en festivos ni en las vacaciones del empleado, y el empleado
 * siempre tiene alguna cita empezada sin marcar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemoSeederTests {

    @Autowired
    DemoSeeder seeder;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    SeededDemo demo;

    @AfterEach
    void deleteCopy() {
        if (demo != null) {
            jdbc.sql("DELETE FROM app_user WHERE id IN (?, ?)").params(demo.ownerUserId(), demo.employeeUserId()).update();
            jdbc.sql("DELETE FROM business WHERE id = ?").param(demo.businessId()).update();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-11-15T12:00:00Z", // domingo: hoy no abre
            "2026-11-16T06:00:00Z", // lunes a las 07:00, antes de abrir
            "2026-10-30T12:00:00Z", // la ventana incluye el cambio de hora (25 de octubre)
            "2027-03-26T18:00:00Z"}) // y el de primavera (28 de marzo)
    void theAgendaIsBelievableAtAnyTime(String instant) {
        Instant now = Instant.parse(instant);
        demo = new TransactionTemplate(transactionManager).execute(_ -> seeder.seed(DemoTemplates.BARBERSHOP, now));

        assertThat(count("SELECT count(*) FROM appointment WHERE business_id = ?")).isBetween(150L, 600L);
        assertThat(count("SELECT count(*) FROM staff_member WHERE business_id = ?")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM service WHERE business_id = ?")).isEqualTo(4);

        // Cada cita, dentro del horario de su día en hora local (domingo cerrado, sábado por la mañana).
        assertThat(rows("""
                SELECT a.id FROM appointment a
                WHERE a.business_id = ? AND NOT EXISTS (
                    SELECT 1 FROM working_hours w
                    WHERE w.business_id = a.business_id AND w.staff_id = a.staff_id
                      AND w.day_of_week = extract(isodow FROM a.starts_at AT TIME ZONE 'Europe/Madrid')
                      AND (a.starts_at AT TIME ZONE 'Europe/Madrid')::time >= w.start_time
                      AND (a.blocked_until AT TIME ZONE 'Europe/Madrid')::time <= w.end_time)""")).isEmpty();
        // Ninguna cita en un cierre del negocio ni en una ausencia del profesional.
        assertThat(rows("""
                SELECT a.id FROM appointment a JOIN closure c ON c.business_id = a.business_id
                WHERE a.business_id = ? AND (c.staff_id IS NULL OR c.staff_id = a.staff_id)
                  AND (a.starts_at AT TIME ZONE 'Europe/Madrid')::date BETWEEN c.start_date AND c.end_date""")).isEmpty();
        // Las horas caen en la rejilla de cuartos de hora: el cambio de hora no las descoloca.
        assertThat(rows("""
                SELECT id FROM appointment WHERE business_id = ?
                  AND extract(minute FROM starts_at AT TIME ZONE 'Europe/Madrid')::int % 15 <> 0""")).isEmpty();
        // Lo que ya ha empezado tiene estado de pasado; lo futuro, confirmado o cancelado.
        assertThat(count("""
                SELECT count(*) FROM appointment WHERE business_id = ? AND starts_at > '%s'
                  AND status IN ('COMPLETED', 'NO_SHOW')""".formatted(instant))).isZero();

        // El empleado (con cuenta) tiene al menos una cita empezada sin marcar.
        assertThat(count("""
                SELECT count(*) FROM appointment a JOIN staff_member s ON s.business_id = a.business_id AND s.id = a.staff_id
                WHERE a.business_id = ? AND s.user_id IS NOT NULL AND s.display_name = 'Empleado Demo'
                  AND a.status = 'CONFIRMED' AND a.starts_at < '%s'""".formatted(instant))).isPositive();
        // Y quedan huecos: hay días futuros con poca ocupación (lo prueba la reserva en DemoApiTests).
        assertThat(count("SELECT count(*) FROM appointment WHERE business_id = ? AND starts_at > '%s'"
                .formatted(instant))).isPositive();
    }

    private long count(String sql) {
        return jdbc.sql(sql).param(demo.businessId()).query(Long.class).single();
    }

    private List<Map<String, Object>> rows(String sql) {
        return jdbc.sql(sql).param(demo.businessId()).query().listOfRows();
    }
}
