package com.slotea.booking;

import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;
import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;

/** El dueño de A no ve ni toca nada de la agenda de B: los ids de B dan 404 y B queda intacto. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AgendaIsolationTests {

    /** {@code {appointment}} y {@code {staff}} se sustituyen por ids de B. */
    record Endpoint(String method, String uri, @Nullable String body) {

        @Override
        public String toString() {
            return method + " " + uri;
        }
    }

    static Stream<Endpoint> ENDPOINTS() {
        return Stream.of(
                new Endpoint("GET", "/api/v1/appointments/{appointment}", null),
                new Endpoint("PUT", "/api/v1/appointments/{appointment}/status", """
                        {"status": "NO_SHOW"}"""),
                new Endpoint("POST", "/api/v1/appointments/{appointment}/cancel", null),
                new Endpoint("GET", "/api/v1/appointments?from={day}&to={day}&staffId={staff}", null));
    }

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    CatalogApi catalog;
    Tenant a;
    Tenant b;
    UUID staffB;
    UUID appointmentB;
    LocalDate day;

    @BeforeEach
    void twoBusinessesWithAppointments() {
        var auth = new AuthApi(mvc, jdbc, passwordEncoder);
        catalog = new CatalogApi(mvc);
        var booking = new BookingApi(mvc);
        a = auth.signup("agenda-a");
        b = auth.signup("agenda-b");
        day = aWeekFromNow();
        UUID serviceA = catalog.createService(a.owner(), "Corte A");
        UUID staffA = booking.createWorkingStaff(a.owner(), "Ana", serviceA);
        assertThat(booking.book(a.slug(), serviceA, staffA, at(day, "09:00"), "a@example.test"))
                .hasStatus(HttpStatus.CREATED);
        UUID serviceB = catalog.createService(b.owner(), "Corte B");
        staffB = booking.createWorkingStaff(b.owner(), "Berta", serviceB);
        var result = booking.book(b.slug(), serviceB, staffB, at(day, "09:00"), "b@example.test");
        assertThat(result).hasStatus(HttpStatus.CREATED);
        appointmentB = UUID.fromString(JsonPath.read(AuthApi.body(result), "$.id"));
        // Ya empezada: así PUT /status no se queda en el 409 de «aún no ha empezado».
        jdbc.sql("""
                UPDATE appointment SET starts_at = now() - interval '1 hour', ends_at = now() - interval '30 minutes',
                       blocked_until = now() - interval '25 minutes' WHERE id = ?""").param(appointmentB).update();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ENDPOINTS")
    void idsOfAnotherBusinessAreNotFound(Endpoint endpoint) {
        String uri = endpoint.uri().replace("{appointment}", appointmentB.toString())
                .replace("{staff}", staffB.toString()).replace("{day}", day.toString());

        assertThat(catalog.send(a.owner(), endpoint.method(), uri, endpoint.body()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);

        assertThat(jdbc.sql("SELECT status FROM appointment WHERE id = ?").param(appointmentB)
                .query(String.class).single()).isEqualTo("CONFIRMED");
        assertThat(jdbc.sql("SELECT count(*) FROM email_outbox WHERE appointment_id = ? AND type = 'BOOKING_CANCELLED'")
                .param(appointmentB).query(Long.class).single()).isZero();
    }

    @Test
    void eachAgendaOnlyContainsItsOwnBusiness() {
        // Desde ayer: la cita de B ya ha empezado (hace una hora); la de A es dentro de una semana.
        String range = "/api/v1/appointments?from=" + LocalDate.now(BookingApi.MADRID).minusDays(1) + "&to=" + day;
        assertThat(catalog.send(a.owner(), "GET", range, null)).hasStatus(HttpStatus.OK).bodyJson()
                .isLenientlyEqualTo("""
                        {"staff": [{"displayName": "Ana"}],
                         "appointments": [{"serviceName": "Corte A", "customer": {"email": "a@example.test"}}]}""");
        assertThat(catalog.send(b.owner(), "GET", range, null)).bodyJson()
                .extractingPath("$.appointments[*].id").asArray().containsExactly(appointmentB.toString());
    }
}
