package com.slotea.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.slotea.TestcontainersConfiguration;

/**
 * La segunda capa del aislamiento: el ESQUEMA. Estos tests se saltan la aplicación (SQL directo, sin
 * {@code @TenantId}) y comprueban que PostgreSQL rechaza por sí solo mezclar negocios y los datos
 * imposibles. Aunque la aplicación tuviera un fallo, la BD no lo dejaría pasar.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CatalogSchemaTests {

    @Autowired
    JdbcClient jdbc;

    UUID businessA;
    UUID businessB;
    UUID staffA;
    UUID staffB;
    UUID serviceA;
    UUID serviceB;

    @BeforeEach
    void twoBusinesses() {
        businessA = insertBusiness();
        businessB = insertBusiness();
        staffA = insertStaff(businessA);
        staffB = insertStaff(businessB);
        serviceA = insertService(businessA, "Corte");
        serviceB = insertService(businessB, "Corte");
    }

    @Test
    void staffServiceCannotMixBusinesses() {
        jdbc.sql("INSERT INTO staff_service VALUES (?, ?, ?)").params(businessA, staffA, serviceA).update();

        // Profesional de A con servicio de B (y al revés): no existe la pareja (business_id, id).
        assertRejected("INSERT INTO staff_service VALUES (?, ?, ?)", businessA, staffA, serviceB);
        assertRejected("INSERT INTO staff_service VALUES (?, ?, ?)", businessB, staffA, serviceB);
    }

    @Test
    void workingHoursAndClosuresCannotPointToAnotherBusinessStaff() {
        assertRejected("""
                INSERT INTO working_hours (id, business_id, staff_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?, 1, '09:00', '14:00')""", UUID.randomUUID(), businessA, staffB);
        assertRejected("""
                INSERT INTO closure (id, business_id, staff_id, start_date, end_date)
                VALUES (?, ?, ?, '2026-12-24', '2026-12-26')""", UUID.randomUUID(), businessA, staffB);
    }

    @Test
    void staffCanOnlyBeLinkedToAMemberOfTheSameBusiness() {
        UUID memberOfB = insertMember(businessB);
        assertRejected("INSERT INTO staff_member (id, business_id, user_id, display_name) VALUES (?, ?, ?, 'X')",
                UUID.randomUUID(), businessA, memberOfB);

        jdbc.sql("INSERT INTO staff_member (id, business_id, user_id, display_name) VALUES (?, ?, ?, 'Yo')")
                .params(UUID.randomUUID(), businessB, memberOfB).update();
    }

    @Test
    void losingTheMembershipUnlinksTheStaffButKeepsIt() {
        UUID member = insertMember(businessA);
        UUID staff = UUID.randomUUID();
        jdbc.sql("INSERT INTO staff_member (id, business_id, user_id, display_name) VALUES (?, ?, ?, 'Ana')")
                .params(staff, businessA, member).update();

        jdbc.sql("DELETE FROM membership WHERE user_id = ?").param(member).update();

        // ON DELETE SET NULL (user_id): la ficha sigue (y su business_id también).
        assertThat(jdbc.sql("SELECT user_id FROM staff_member WHERE id = ?").param(staff)
                .query(UUID.class).optional()).isEmpty();
        assertThat(jdbc.sql("SELECT business_id FROM staff_member WHERE id = ?").param(staff)
                .query(UUID.class).single()).isEqualTo(businessA);
    }

    @Test
    void overlappingWorkingHoursOnTheSameDayAreRejected() {
        insertHours(staffA, 1, "09:00", "14:00");
        insertHours(staffA, 1, "14:00", "20:00"); // contiguo ('[)'): jornada partida sin hueco
        insertHours(staffA, 2, "10:00", "13:00"); // otro día
        insertHours(staffB, 1, "10:00", "13:00"); // otro profesional (y otro negocio)

        assertRejected("""
                INSERT INTO working_hours (id, business_id, staff_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?, 1, '13:30', '15:00')""", UUID.randomUUID(), businessA, staffA);
    }

    @Test
    void impossibleValuesAreRejected() {
        assertRejected("""
                INSERT INTO working_hours (id, business_id, staff_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?, 1, '14:00', '09:00')""", UUID.randomUUID(), businessA, staffA);
        assertRejected("""
                INSERT INTO working_hours (id, business_id, staff_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?, 8, '09:00', '14:00')""", UUID.randomUUID(), businessA, staffA);
        assertRejected("""
                INSERT INTO closure (id, business_id, start_date, end_date)
                VALUES (?, ?, '2026-12-26', '2026-12-24')""", UUID.randomUUID(), businessA);
        assertRejected("INSERT INTO service (id, business_id, name, duration_min) VALUES (?, ?, 'Raro', 7)",
                UUID.randomUUID(), businessA);
        assertRejected("""
                INSERT INTO service (id, business_id, name, duration_min, price_cents)
                VALUES (?, ?, 'Gratis+', 30, -1)""", UUID.randomUUID(), businessA);
    }

    @Test
    void serviceNamesAreUniquePerBusinessIgnoringCase() {
        assertRejected("INSERT INTO service (id, business_id, name, duration_min) VALUES (?, ?, 'CORTE', 30)",
                UUID.randomUUID(), businessA);
        // B ya tiene su propio «Corte»: cada negocio tiene su espacio de nombres.
        assertThat(jdbc.sql("SELECT count(*) FROM service WHERE lower(name) = 'corte' AND business_id IN (?, ?)")
                .params(businessA, businessB).query(Long.class).single()).isEqualTo(2);
    }

    private void assertRejected(String sql, Object... params) {
        assertThatThrownBy(() -> jdbc.sql(sql).params(params).update()).isInstanceOf(DataAccessException.class);
    }

    private UUID insertBusiness() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO business (id, slug, name, contact_email) VALUES (?, ?, 'Test', 'x@example.test')")
                .params(id, "c-" + id.toString().substring(0, 8)).update();
        return id;
    }

    private UUID insertStaff(UUID businessId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO staff_member (id, business_id, display_name) VALUES (?, ?, 'Profesional')")
                .params(id, businessId).update();
        return id;
    }

    private UUID insertService(UUID businessId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO service (id, business_id, name, duration_min) VALUES (?, ?, ?, 30)")
                .params(id, businessId, name).update();
        return id;
    }

    private UUID insertMember(UUID businessId) {
        UUID userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user (id, email, password_hash, full_name) VALUES (?, ?, '{noop}x', 'Miembro')")
                .params(userId, userId + "@example.test").update();
        jdbc.sql("INSERT INTO membership (id, business_id, user_id, role) VALUES (?, ?, ?, 'STAFF')")
                .params(UUID.randomUUID(), businessId, userId).update();
        return userId;
    }

    private void insertHours(UUID staffId, int day, String start, String end) {
        UUID businessId = staffId.equals(staffA) ? businessA : businessB;
        jdbc.sql("""
                INSERT INTO working_hours (id, business_id, staff_id, day_of_week, start_time, end_time)
                VALUES (?, ?, ?, ?, ?::time, ?::time)""")
                .params(UUID.randomUUID(), businessId, staffId, day, start, end).update();
    }
}
