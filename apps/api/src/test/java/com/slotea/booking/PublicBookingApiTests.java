package com.slotea.booking;

import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;

/** La reserva pública de punta a punta (HTTP sin sesión → BD real). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicBookingApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    CatalogApi catalog;
    BookingApi booking;
    Tenant tenant;
    UUID haircut;
    UUID ana;
    LocalDate day;

    @BeforeEach
    void bookableBusiness() {
        catalog = new CatalogApi(mvc);
        booking = new BookingApi(mvc);
        tenant = new AuthApi(mvc, jdbc, passwordEncoder).signup("public");
        haircut = catalog.createService(tenant.owner(), "Corte"); // 30 min + 5 de margen
        ana = booking.createWorkingStaff(tenant.owner(), "Ana", haircut);
        day = aWeekFromNow();
    }

    // --- Página del negocio ---------------------------------------------------------------------

    @Test
    void businessPageShowsOnlyWhatCanBeBooked() {
        UUID shave = catalog.createService(tenant.owner(), "Afeitado"); // nadie lo hace
        UUID retired = catalog.createService(tenant.owner(), "Tinte");
        booking.createWorkingStaff(tenant.owner(), "Berta"); // sin servicios
        UUID carlos = booking.createWorkingStaff(tenant.owner(), "Carlos", haircut, retired);
        catalog.send(tenant.owner(), "PUT", "/api/v1/services/" + retired, """
                {"name": "Tinte", "durationMin": 60, "active": false}""");
        UUID dani = booking.createWorkingStaff(tenant.owner(), "Dani", haircut);
        catalog.send(tenant.owner(), "PUT", "/api/v1/staff/" + dani, """
                {"displayName": "Dani", "active": false}""");

        assertThat(booking.business(tenant.slug())).hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
                {"slug": "%s", "name": "Negocio public", "timezone": "Europe/Madrid",
                 "services": [{"id": "%s", "name": "Corte", "durationMin": 30, "priceCents": 1500}],
                 "staff": [{"id": "%s", "displayName": "Ana", "serviceIds": ["%s"]},
                           {"id": "%s", "displayName": "Carlos", "serviceIds": ["%s"]}]}
                """.formatted(tenant.slug(), haircut, ana, haircut, carlos, haircut));
        assertThat(AuthApi.body(booking.business(tenant.slug()))).doesNotContain(shave.toString(), "bufferMin");
    }

    @Test
    void unknownSlugIsNotFound() {
        assertThat(booking.business("no-existe")).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(booking.availability("no-existe", haircut, null, day, day)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.book("no-existe", haircut, null, at(day, "09:00"), "x@example.test"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- Disponibilidad -------------------------------------------------------------------------

    @Test
    void availabilityListsEveryDayAndItsSlotsInLocalTime() {
        var result = booking.availability(tenant.slug(), haircut, null, day, day.plusDays(1));

        assertThat(result).hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.timezone").isEqualTo("Europe/Madrid");
        // De 09:00 a 14:00, cada 15 min, mientras quepan los 30 min del servicio: 09:00 … 13:30.
        assertThat(result).bodyJson().extractingPath("$.days[0].slots[*].time").asArray()
                .hasSize(19).startsWith("09:00", "09:15").endsWith("13:30");
        assertThat(result).bodyJson().extractingPath("$.days[0].slots[0].startsAt")
                .isEqualTo(at(day, "09:00").toString());
        assertThat(result).bodyJson().extractingPath("$.days[*].date").asArray()
                .containsExactly(day.toString(), day.plusDays(1).toString());
    }

    @Test
    void pastDaysClosuresAndTheHorizonHaveNoSlots() {
        LocalDate today = LocalDate.now(BookingApi.MADRID);
        assertThat(booking.availability(tenant.slug(), haircut, null, today.minusDays(2), today.minusDays(1)))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.days[*].slots[*]").asArray().isEmpty();

        LocalDate beyond = today.plusDays(BookingPolicy.HORIZON_DAYS + 1);
        assertThat(booking.availability(tenant.slug(), haircut, null, beyond, beyond))
                .bodyJson().extractingPath("$.days[0].slots").asArray().isEmpty();

        catalog.createClosure(tenant.owner(), null, day.toString(), day.toString());
        assertThat(booking.availability(tenant.slug(), haircut, null, day, day))
                .bodyJson().extractingPath("$.days[0].slots").asArray().isEmpty();
    }

    @Test
    void invalidRangesAreRejected() {
        assertThat(booking.availability(tenant.slug(), haircut, null, day, day.minusDays(1)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(booking.availability(tenant.slug(), haircut, null, day, day.plusDays(14)))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(booking.availability(tenant.slug(), haircut, null, day, day.plusDays(13)))
                .hasStatus(HttpStatus.OK);
    }

    @Test
    void inactiveServicesAndStaffWhoDoNotOfferTheServiceAreNotFound() {
        UUID shave = catalog.createService(tenant.owner(), "Afeitado");
        UUID berta = booking.createWorkingStaff(tenant.owner(), "Berta", shave);

        assertThat(booking.availability(tenant.slug(), haircut, berta, day, day)).hasStatus(HttpStatus.NOT_FOUND);

        catalog.send(tenant.owner(), "PUT", "/api/v1/services/" + haircut, """
                {"name": "Corte", "durationMin": 30, "active": false}""");
        assertThat(booking.availability(tenant.slug(), haircut, null, day, day)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.book(tenant.slug(), haircut, null, at(day, "09:00"), "x@example.test"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- Reservar -------------------------------------------------------------------------------

    @Test
    void bookingCreatesTheAppointmentAndTakesTheSlot() {
        var result = booking.book(tenant.slug(), haircut, ana, at(day, "09:00"), "Laura@Example.TEST");

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                {"serviceName": "Corte", "staffName": "Ana", "date": "%s", "time": "09:00",
                 "timezone": "Europe/Madrid", "customerEmail": "laura@example.test",
                 "startsAt": "%s", "endsAt": "%s"}""".formatted(day, at(day, "09:00"), at(day, "09:30")));

        // Ocupado hasta las 09:35 (30 + 5 de margen): 09:15 y 09:30 desaparecen; 09:45 sigue libre.
        assertThat(booking.availability(tenant.slug(), haircut, ana, day, day))
                .bodyJson().extractingPath("$.days[0].slots[*].time").asArray()
                .startsWith("09:45").doesNotContain("09:00", "09:15", "09:30");

        var row = jdbc.sql("""
                SELECT a.status, a.blocked_until, c.email, c.phone FROM appointment a
                JOIN customer c ON c.business_id = a.business_id AND c.id = a.customer_id
                WHERE a.business_id = ?""").param(tenant.businessId()).query().singleRow();
        assertThat(row).containsEntry("status", "CONFIRMED").containsEntry("email", "laura@example.test")
                .containsEntry("phone", "+34 600 000 000");
        assertThat(((java.sql.Timestamp) row.get("blocked_until")).toInstant()).isEqualTo(at(day, "09:35"));
    }

    @Test
    void theSameSlotCannotBeBookedTwice() {
        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "10:00"), "a@example.test"))
                .hasStatus(HttpStatus.CREATED);

        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "10:00"), "b@example.test"))
                .hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(booking.book(tenant.slug(), haircut, null, at(day, "10:15"), "c@example.test"))
                .as("solape parcial, con «cualquier profesional»")
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void anyStaffGoesToTheLeastBusyThenByName() {
        booking.createWorkingStaff(tenant.owner(), "Luis", haircut);
        booking.book(tenant.slug(), haircut, ana, at(day, "09:00"), "a@example.test");

        // Ana ya tiene una cita ese día: la siguiente va para Luis.
        assertThat(booking.book(tenant.slug(), haircut, null, at(day, "11:00"), "b@example.test"))
                .hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.staffName").isEqualTo("Luis");
        // Empate (una cada uno): por nombre.
        assertThat(booking.book(tenant.slug(), haircut, null, at(day, "12:00"), "c@example.test"))
                .bodyJson().extractingPath("$.staffName").isEqualTo("Ana");
    }

    @Test
    void onlyOfferedSlotsCanBeBooked() {
        LocalDate today = LocalDate.now(BookingApi.MADRID);
        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "15:00"), "x@example.test"))
                .as("fuera del horario").hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "13:45"), "x@example.test"))
                .as("el servicio acabaría después de cerrar").hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "09:05"), "x@example.test"))
                .as("fuera de la rejilla de 15 min").hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.book(tenant.slug(), haircut, ana, at(today.minusDays(1), "09:00"), "x@example.test"))
                .as("en el pasado").hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.book(tenant.slug(), haircut, ana,
                at(today.plusDays(BookingPolicy.HORIZON_DAYS + 1), "09:00"), "x@example.test"))
                .as("más allá del horizonte").hasStatus(HttpStatus.CONFLICT);

        catalog.createClosure(tenant.owner(), ana, day.toString(), day.toString());
        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "09:00"), "x@example.test"))
                .as("ausencia del profesional").hasStatus(HttpStatus.CONFLICT);

        assertThat(jdbc.sql("SELECT count(*) FROM appointment WHERE business_id = ?").param(tenant.businessId())
                .query(Long.class).single()).isZero();
    }

    @Test
    void invalidBookingsAreRejectedFieldByField() {
        assertThat(booking.bookJson(tenant.slug(), """
                {"serviceId": "%s", "startsAt": "%s",
                 "customer": {"fullName": " ", "email": "no-es-email", "phone": "abc"}}
                """.formatted(haircut, at(day, "09:00"))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray()
                .contains("customer.fullName", "customer.email", "customer.phone");
    }

    @Test
    void anExistingCustomerIsReusedButNotOverwritten() {
        booking.book(tenant.slug(), haircut, ana, at(day, "09:00"), "pepe@example.test");
        booking.bookJson(tenant.slug(), """
                {"serviceId": "%s", "startsAt": "%s",
                 "customer": {"fullName": "Otro nombre", "email": "PEPE@example.test"}}
                """.formatted(haircut, at(day, "11:00")));

        var customers = jdbc.sql("SELECT full_name FROM customer WHERE business_id = ?")
                .param(tenant.businessId()).query(String.class).list();
        assertThat(customers).containsExactly("Cliente de prueba");
        assertThat(jdbc.sql("SELECT count(DISTINCT customer_id) FROM appointment WHERE business_id = ?")
                .param(tenant.businessId()).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void bookingWithoutCsrfIsForbidden() {
        assertThat(mvc.post().uri("/api/v1/public/businesses/{slug}/appointments", tenant.slug())
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
