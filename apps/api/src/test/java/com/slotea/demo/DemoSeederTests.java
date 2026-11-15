package com.slotea.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.demo.DemoDtos.DemoSector;
import com.slotea.demo.DemoSeeder.SeededDemo;
import com.slotea.shared.validation.BrandColor;

/**
 * El seed de cada sector crea una agenda creíble a cualquier hora: dentro del horario de cada
 * profesional (en hora de Madrid, también la semana del cambio de hora), cada uno con sus servicios,
 * nada en festivos ni en las vacaciones del empleado, y quien tiene cuenta siempre tiene alguna cita
 * empezada sin marcar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DemoSeederTests {

    static final List<String> INSTANTS = List.of(
            "2026-11-15T12:00:00Z", // domingo: hoy no abre
            "2026-11-16T06:00:00Z", // lunes a las 07:00, antes de abrir
            "2026-10-30T12:00:00Z", // la ventana incluye el cambio de hora (25 de octubre)
            "2027-03-26T18:00:00Z"); // y el de primavera (28 de marzo)

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
            jdbc.sql("DELETE FROM app_user WHERE id IN (SELECT user_id FROM membership WHERE business_id = ?)")
                    .param(demo.businessId()).update();
            jdbc.sql("DELETE FROM business WHERE id = ?").param(demo.businessId()).update();
        }
    }

    static Stream<Arguments> everySectorAtTrickyTimes() {
        return Arrays.stream(DemoSector.values())
                .flatMap(sector -> INSTANTS.stream().map(instant -> Arguments.of(sector, instant)));
    }

    @ParameterizedTest(name = "{0} @ {1}")
    @MethodSource("everySectorAtTrickyTimes")
    void theAgendaIsBelievableAtAnyTime(DemoSector sector, String instant) {
        DemoTemplate template = DemoTemplates.of(sector);
        demo = seed(template, Instant.parse(instant));

        assertThat(count("SELECT count(*) FROM appointment WHERE business_id = ?")).isBetween(40L, 600L);
        assertThat(count("SELECT count(*) FROM staff_member WHERE business_id = ?")).isEqualTo(template.staff().size());
        assertThat(count("SELECT count(*) FROM service WHERE business_id = ?")).isEqualTo(template.services().size());
        assertThat(jdbc.sql("SELECT brand_color FROM business WHERE id = ?").param(demo.businessId())
                .query(String.class).single()).isEqualTo(template.brandColor());

        // Cada cita, dentro del horario de su profesional en hora local.
        assertThat(rows("""
                SELECT a.id FROM appointment a
                WHERE a.business_id = ? AND NOT EXISTS (
                    SELECT 1 FROM working_hours w
                    WHERE w.business_id = a.business_id AND w.staff_id = a.staff_id
                      AND w.day_of_week = extract(isodow FROM a.starts_at AT TIME ZONE 'Europe/Madrid')
                      AND (a.starts_at AT TIME ZONE 'Europe/Madrid')::time >= w.start_time
                      AND (a.blocked_until AT TIME ZONE 'Europe/Madrid')::time <= w.end_time)""")).isEmpty();
        // Y de un servicio que ese profesional hace.
        assertThat(rows("""
                SELECT a.id FROM appointment a
                WHERE a.business_id = ? AND NOT EXISTS (
                    SELECT 1 FROM staff_service ss
                    WHERE ss.business_id = a.business_id AND ss.staff_id = a.staff_id
                      AND ss.service_id = a.service_id)""")).isEmpty();
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

        // Cada profesional con cuenta tiene al menos una cita empezada sin marcar.
        assertThat(rows("""
                SELECT s.id FROM staff_member s
                WHERE s.business_id = ? AND s.user_id IS NOT NULL AND NOT EXISTS (
                    SELECT 1 FROM appointment a
                    WHERE a.business_id = s.business_id AND a.staff_id = s.id
                      AND a.status = 'CONFIRMED' AND a.starts_at < '%s')""".formatted(instant))).isEmpty();
        // Y quedan citas por delante (los huecos libres los prueba la reserva en DemoApiTests).
        assertThat(count("SELECT count(*) FROM appointment WHERE business_id = ? AND starts_at > '%s'"
                .formatted(instant))).isPositive();
    }

    /** En el selector de sector se distinguen de un vistazo, y ninguno es el neutro de un negocio nuevo. */
    @Test
    void eachSectorHasItsOwnBrandColor() {
        List<String> colors = Arrays.stream(DemoSector.values()).map(s -> DemoTemplates.of(s).brandColor()).toList();

        assertThat(colors).doesNotHaveDuplicates().doesNotContain(BrandColor.DEFAULT);
    }

    @Test
    void psychologyIsASingleProfessionalWithHourlySessions() {
        demo = seed(DemoTemplates.PSYCHOLOGY, Instant.parse(INSTANTS.get(1)));

        assertThat(demo.employeeUserId()).isNull();
        assertThat(count("SELECT count(*) FROM membership WHERE business_id = ?")).isOne();
        assertThat(count("SELECT count(*) FROM staff_member WHERE business_id = ? AND user_id IS NOT NULL")).isOne();
        // Sin empleado no hay vacaciones: solo el festivo del negocio.
        assertThat(count("SELECT count(*) FROM closure WHERE business_id = ?")).isOne();
        // 50 minutos de sesión más 10 de margen, y siempre en punto.
        assertThat(rows("""
                SELECT id FROM appointment WHERE business_id = ?
                  AND (ends_at - starts_at <> interval '50 minutes'
                       OR blocked_until - starts_at <> interval '60 minutes'
                       OR extract(minute FROM starts_at AT TIME ZONE 'Europe/Madrid') <> 0)""")).isEmpty();
    }

    @Test
    void sectorsWithAnEmployeeGiveThemAccountAndVacation() {
        demo = seed(DemoTemplates.MECHANIC, Instant.parse(INSTANTS.get(1)));

        assertThat(demo.employeeUserId()).isNotNull();
        assertThat(count("SELECT count(*) FROM membership WHERE business_id = ? AND role = 'STAFF'")).isOne();
        assertThat(count("""
                SELECT count(*) FROM closure c JOIN staff_member s ON s.business_id = c.business_id AND s.id = c.staff_id
                WHERE c.business_id = ? AND s.display_name = 'Empleado Demo'""")).isOne();
    }

    private SeededDemo seed(DemoTemplate template, Instant now) {
        return new TransactionTemplate(transactionManager).execute(_ -> seeder.seed(template, now));
    }

    private long count(String sql) {
        return jdbc.sql(sql).param(demo.businessId()).query(Long.class).single();
    }

    private List<Map<String, Object>> rows(String sql) {
        return jdbc.sql(sql).param(demo.businessId()).query().listOfRows();
    }
}
