package com.slotea.booking;

import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.shared.security.OpaqueTokens;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.DatabaseErrors;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;

/**
 * La doble reserva es imposible aunque las peticiones lleguen a la vez. PostgreSQL real
 * (Testcontainers), nunca H2: lo que se prueba es la restricción {@code EXCLUDE} de la BD.
 *
 * <p>Todos los hilos esperan en un {@link CountDownLatch} y salen a la vez, para que compitan de verdad.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingConcurrencyTests {

    static final int THREADS = 20;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    AppointmentRepository appointments;

    @Autowired
    Customers customers;

    @Autowired
    PlatformTransactionManager transactionManager;

    BookingApi booking;
    Tenant tenant;
    UUID haircut;
    UUID ana;
    LocalDate day;

    @BeforeEach
    void bookableBusiness() {
        booking = new BookingApi(mvc);
        tenant = new AuthApi(mvc, jdbc, passwordEncoder).signup("race");
        haircut = new CatalogApi(mvc).createService(tenant.owner(), "Corte");
        ana = booking.createWorkingStaff(tenant.owner(), "Ana", haircut);
        day = aWeekFromNow();
    }

    @Test
    void twentyClientsBookingTheSameSlotAtOnceGetExactlyOneAppointment() throws Exception {
        Instant slot = at(day, "10:00");

        List<Integer> statuses = race(THREADS, i -> () -> booking
                .book(tenant.slug(), haircut, ana, slot, "cliente" + i + "@example.test")
                .getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(THREADS - 1);
        assertThat(confirmedAppointments()).isEqualTo(1);
    }

    @Test
    void theDatabaseAloneRejectsTheDuplicatesEvenWithoutTheApplicationChecks() throws Exception {
        // Saltándonos AvailabilityService y la cola de BookingService: INSERT del mismo hueco a la vez,
        // directamente por JPA. Sin la cola hay deadlocks y PostgreSQL resuelve uno por segundo
        // (deadlock_timeout): con 5 hilos basta para demostrarlo sin alargar la CI.
        int threads = 5;
        Instant start = at(day, "10:00");
        UUID customer = TenantContext.callAs(tenant.businessId(), () -> new TransactionTemplate(transactionManager)
                .execute(_ -> customers.findOrCreate("race@example.test", "Carrera", null)));

        List<String> outcomes = race(threads, _ -> () -> {
            try {
                TenantContext.callAs(tenant.businessId(), () -> new TransactionTemplate(transactionManager)
                        .execute(_ -> appointments.saveAndFlush(new Appointment(ana, haircut, customer, start,
                                start.plus(Duration.ofMinutes(30)), start.plus(Duration.ofMinutes(35)),
                                OpaqueTokens.sha256(OpaqueTokens.generate()), null, Instant.now()))));
                return "created";
            } catch (DataAccessException e) {
                return DatabaseErrors.sqlState(e) + " " + DatabaseErrors.violatedConstraint(e);
            }
        });

        // Los demás: 23P01 (la ganadora ya había confirmado) o 40P01 (dos a medias se esperaban
        // mutuamente y PostgreSQL abortó a una; ver BookingService). Nunca dos citas.
        assertThat(outcomes).as(outcomes.toString()).filteredOn("created"::equals).hasSize(1);
        assertThat(outcomes).as(outcomes.toString()).filteredOn(o -> !o.equals("created"))
                .hasSize(threads - 1)
                .allMatch(o -> o.equals("23P01 appointment_no_overlap") || o.startsWith("40P01"));
        assertThat(confirmedAppointments()).isEqualTo(1);
    }

    @Test
    void anyStaffSpreadsConcurrentBookingsAndRejectsOnlyTheSurplus() throws Exception {
        booking.createWorkingStaff(tenant.owner(), "Luis", haircut);
        Instant slot = at(day, "10:00");

        // Tres clientes a la vez, dos profesionales libres: dos citas (una por profesional) y un 409.
        List<Integer> statuses = race(3, i -> () -> booking
                .book(tenant.slug(), haircut, null, slot, "cliente" + i + "@example.test")
                .getResponse().getStatus());

        assertThat(statuses).containsExactlyInAnyOrder(201, 201, 409);
        assertThat(jdbc.sql("SELECT count(DISTINCT staff_id) FROM appointment WHERE business_id = ?")
                .param(tenant.businessId()).query(Long.class).single()).isEqualTo(2);
    }

    /** Lanza {@code count} tareas que arrancan a la vez y devuelve sus resultados. */
    private static <T> List<T> race(int count, IntFunction<Callable<T>> task) throws Exception {
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(count)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Callable<T> work = task.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return work.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(30, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private long confirmedAppointments() {
        return jdbc.sql("SELECT count(*) FROM appointment WHERE business_id = ? AND status = 'CONFIRMED'")
                .param(tenant.businessId()).query(Long.class).single();
    }
}
