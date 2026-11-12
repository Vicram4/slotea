package com.slotea.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
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
 * La garantía contra la doble reserva, probada contra el ESQUEMA (SQL directo, sin pasar por la
 * aplicación): aunque el código Java tuviera un fallo, PostgreSQL no dejaría pasar un solape.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BookingSchemaTests {

    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    JdbcClient jdbc;

    UUID businessA;
    UUID businessB;
    UUID ana;
    UUID luis;
    UUID staffOfB;
    UUID serviceA;
    UUID serviceB;
    UUID customerA;
    UUID customerB;

    @BeforeEach
    void twoBusinesses() {
        businessA = insertBusiness();
        businessB = insertBusiness();
        ana = insertStaff(businessA);
        luis = insertStaff(businessA);
        staffOfB = insertStaff(businessB);
        serviceA = insertService(businessA);
        serviceB = insertService(businessB);
        customerA = insertCustomer(businessA, "cliente@example.test");
        customerB = insertCustomer(businessB, "cliente@example.test");
    }

    @Test
    void partialOverlapIsRejected() {
        book(ana, "10:00", "10:30", "10:30");

        assertRejected(() -> book(ana, "10:15", "10:45", "10:45"));
        assertRejected(() -> book(ana, "09:45", "10:15", "10:15"));
        assertRejected(() -> book(ana, "09:00", "12:00", "12:00")); // la envuelve entera
    }

    @Test
    void theBufferCountsAsOccupied() {
        book(ana, "10:00", "10:30", "10:40");

        assertRejected(() -> book(ana, "10:30", "11:00", "11:00"));
        book(ana, "10:40", "11:10", "11:10");
    }

    @Test
    void backToBackAppointmentsAreAllowed() {
        book(ana, "10:00", "10:30", "10:30");
        book(ana, "10:30", "11:00", "11:00");
        book(ana, "09:30", "10:00", "10:00");
    }

    @Test
    void cancellingFreesTheSlot() {
        UUID first = book(ana, "10:00", "10:30", "10:30");
        jdbc.sql("UPDATE appointment SET status = 'CANCELLED', cancelled_at = now() WHERE id = ?").param(first).update();

        book(ana, "10:00", "10:30", "10:30");
        // Reactivar la cancelada chocaría con la nueva.
        assertRejected(() -> jdbc.sql("UPDATE appointment SET status = 'CONFIRMED', cancelled_at = NULL WHERE id = ?")
                .param(first).update());
    }

    @Test
    void differentStaffCanTakeTheSameSlot() {
        book(ana, "10:00", "10:30", "10:30");
        book(luis, "10:00", "10:30", "10:30");
    }

    @Test
    void anAppointmentCannotMixBusinesses() {
        assertRejected(() -> insert(businessA, staffOfB, serviceA, customerA, "10:00", "10:30", "10:30"));
        assertRejected(() -> insert(businessA, ana, serviceB, customerA, "10:00", "10:30", "10:30"));
        assertRejected(() -> insert(businessA, ana, serviceA, customerB, "10:00", "10:30", "10:30"));
    }

    @Test
    void impossibleValuesAreRejected() {
        assertRejected(() -> book(ana, "10:30", "10:00", "10:30")); // acaba antes de empezar
        assertRejected(() -> book(ana, "10:00", "10:30", "10:20")); // bloquea menos de lo que dura
        assertRejected(() -> jdbc.sql("""
                INSERT INTO appointment (id, business_id, staff_id, service_id, customer_id, starts_at, ends_at,
                    blocked_until, status, manage_token_hash)
                VALUES (?, ?, ?, ?, ?, now(), now() + interval '30 min', now() + interval '30 min', 'CANCELLED', ?)
                """).params(UUID.randomUUID(), businessA, ana, serviceA, customerA, token()).update()); // sin cancelled_at
        assertRejected(() -> jdbc.sql("INSERT INTO customer (id, business_id, email, full_name) VALUES (?, ?, ?, 'X')")
                .params(UUID.randomUUID(), businessA, "Mayus@example.test").update());
        assertRejected(() -> insertCustomer(businessA, "cliente@example.test")); // duplicado en el mismo negocio
    }

    @Test
    void deletingTheBusinessDeletesItsAppointments() {
        book(ana, "10:00", "10:30", "10:30");

        jdbc.sql("DELETE FROM business WHERE id = ?").param(businessA).update();

        assertThat(jdbc.sql("SELECT count(*) FROM appointment WHERE business_id = ?").param(businessA)
                .query(Long.class).single()).isZero();
    }

    @Test
    void staffWithAppointmentsCannotBeDeleted() {
        book(ana, "10:00", "10:30", "10:30");

        assertRejected(() -> jdbc.sql("DELETE FROM staff_member WHERE id = ?").param(ana).update());
    }

    // --- helpers ---

    /** Cita en el negocio A el 11-01-2027 (horas en UTC). */
    private UUID book(UUID staff, String start, String end, String blockedUntil) {
        return insert(businessA, staff, serviceA, customerA, start, end, blockedUntil);
    }

    private UUID insert(UUID business, UUID staff, UUID service, UUID customer, String start, String end,
            String blockedUntil) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO appointment (id, business_id, staff_id, service_id, customer_id, starts_at, ends_at,
                    blocked_until, manage_token_hash)
                VALUES (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?)
                """)
                .params(id, business, staff, service, customer, at(start), at(end), at(blockedUntil), token())
                .update();
        return id;
    }

    private static String at(String time) {
        return "2027-01-11T" + time + ":00Z";
    }

    private static byte[] token() {
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        return hash;
    }

    private void assertRejected(Runnable insert) {
        assertThatThrownBy(insert::run).isInstanceOf(DataAccessException.class);
    }

    private UUID insertBusiness() {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO business (id, slug, name, contact_email) VALUES (?, ?, 'Test', 'x@example.test')")
                .params(id, "b-" + id.toString().substring(0, 8)).update();
        return id;
    }

    private UUID insertStaff(UUID businessId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO staff_member (id, business_id, display_name) VALUES (?, ?, 'Profesional')")
                .params(id, businessId).update();
        return id;
    }

    private UUID insertService(UUID businessId) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO service (id, business_id, name, duration_min) VALUES (?, ?, 'Corte', 30)")
                .params(id, businessId).update();
        return id;
    }

    private UUID insertCustomer(UUID businessId, String email) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO customer (id, business_id, email, full_name) VALUES (?, ?, ?, 'Cliente')")
                .params(id, businessId, email).update();
        return id;
    }
}
