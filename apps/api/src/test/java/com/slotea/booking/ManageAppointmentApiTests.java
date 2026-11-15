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
import com.slotea.support.Mailpit;

/** El enlace del email: ver, cancelar y cambiar la cita sin cuenta (HTTP → BD real → Mailpit). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ManageAppointmentApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Mailpit mailpit;

    CatalogApi catalog;
    BookingApi booking;
    Tenant tenant;
    UUID haircut;
    UUID ana;
    LocalDate day;
    String email;
    String token;

    @BeforeEach
    void bookedAppointment() {
        catalog = new CatalogApi(mvc);
        booking = new BookingApi(mvc);
        tenant = new AuthApi(mvc, jdbc, passwordEncoder).signup("manage");
        haircut = catalog.createService(tenant.owner(), "Corte"); // 30 min + 5 de margen
        ana = booking.createWorkingStaff(tenant.owner(), "Ana", haircut);
        day = aWeekFromNow();
        email = AuthApi.unique("laura") + "@example.test";
        token = booking.bookForToken(tenant.slug(), haircut, ana, at(day, "09:00"), email);
    }

    // --- Ver ------------------------------------------------------------------------------------

    @Test
    void theLinkShowsTheAppointment() {
        assertThat(booking.managed(tenant.slug(), token)).hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
                {"status": "CONFIRMED", "serviceId": "%s", "serviceName": "Corte", "staffId": "%s",
                 "staffName": "Ana", "customerName": "Cliente de prueba", "date": "%s", "time": "09:00",
                 "endTime": "09:30", "timezone": "Europe/Madrid", "changeable": true,
                 "startsAt": "%s", "changeableUntil": "%s"}
                """.formatted(haircut, ana, day, at(day, "09:00"), at(day, "07:00")));
    }

    @Test
    void invalidTokensAreNotFound() {
        String forged = "A".repeat(43);
        assertThat(booking.managed(tenant.slug(), forged)).hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(booking.managed(tenant.slug(), "abc")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.cancel(tenant.slug(), forged)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.reschedule(tenant.slug(), forged, at(day, "10:00"), null)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- Cancelar -------------------------------------------------------------------------------

    @Test
    void cancellingFreesTheSlotAndSendsAnEmailOnce() {
        assertThat(booking.cancel(tenant.slug(), token)).hasStatus(HttpStatus.OK).bodyJson()
                .isLenientlyEqualTo("""
                        {"status": "CANCELLED", "changeable": false}""");

        assertThat(booking.availability(tenant.slug(), haircut, ana, day, day))
                .bodyJson().extractingPath("$.days[0].slots[*].time").asArray().startsWith("09:00");
        assertThat(mailpit.await(email, 2)).extracting(Mailpit.Message::subject)
                .anySatisfy(subject -> assertThat(subject).startsWith("Cita cancelada: Corte"));

        // Segundo clic (o doble envío del formulario): ni error ni otro email.
        assertThat(booking.cancel(tenant.slug(), token)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT count(*) FROM email_outbox WHERE to_email = ?").param(email)
                .query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void changesNeedTheCsrfToken() {
        String url = "/api/v1/public/businesses/" + tenant.slug() + "/appointments/manage/" + token + "/cancel";
        assertThat(mvc.post().uri(url)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(booking.managed(tenant.slug(), token)).bodyJson().extractingPath("$.status").isEqualTo("CONFIRMED");
    }

    @Test
    void lessThanTwoHoursBeforeNothingCanBeChanged() {
        jdbc.sql("""
                UPDATE appointment SET starts_at = now() + interval '90 minutes',
                    ends_at = now() + interval '120 minutes', blocked_until = now() + interval '125 minutes'
                WHERE business_id = ?""").param(tenant.businessId()).update();

        assertThat(booking.managed(tenant.slug(), token)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.changeable").isEqualTo(false);
        assertThat(booking.cancel(tenant.slug(), token)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("menos de 2 horas");
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "10:00"), null)).hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.manageAvailability(tenant.slug(), token, day, day)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void finishedAppointmentsCannotBeChanged() {
        jdbc.sql("UPDATE appointment SET status = 'COMPLETED' WHERE business_id = ?").param(tenant.businessId()).update();

        assertThat(booking.cancel(tenant.slug(), token)).hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "10:00"), null)).hasStatus(HttpStatus.CONFLICT);
    }

    // --- Cambiar de hora ------------------------------------------------------------------------

    @Test
    void theAppointmentDoesNotBlockItsOwnMove() {
        // 09:15 se solapa con la propia cita (09:00-09:35): para moverla, sí está libre.
        assertThat(booking.manageAvailability(tenant.slug(), token, day, day)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.days[0].slots[*].time").asArray().startsWith("09:00", "09:15");
        assertThat(booking.availability(tenant.slug(), haircut, null, day, day))
                .bodyJson().extractingPath("$.days[0].slots[*].time").asArray().doesNotContain("09:15");

        assertThat(booking.reschedule(tenant.slug(), token, at(day, "09:15"), null)).hasStatus(HttpStatus.OK)
                .bodyJson().isLenientlyEqualTo("""
                        {"time": "09:15", "endTime": "09:45", "staffName": "Ana"}""");
    }

    @Test
    void reschedulingMovesTheSameAppointmentKeepsTheLinkAndSendsAnEmail() {
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "11:00"), ana)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.time").isEqualTo("11:00");

        // El mismo enlace sigue sirviendo; el hueco viejo queda libre y el nuevo, ocupado.
        assertThat(booking.managed(tenant.slug(), token)).bodyJson().extractingPath("$.time").isEqualTo("11:00");
        assertThat(booking.availability(tenant.slug(), haircut, ana, day, day))
                .bodyJson().extractingPath("$.days[0].slots[*].time").asArray()
                .contains("09:00").doesNotContain("11:00");
        assertThat(jdbc.sql("SELECT count(*) FROM appointment WHERE business_id = ?").param(tenant.businessId())
                .query(Long.class).single()).isEqualTo(1);

        var changed = mailpit.await(email, 2).stream()
                .filter(m -> m.subject().startsWith("Cita cambiada")).findFirst().orElseThrow();
        assertThat(changed.subject()).endsWith("a las 11:00");
        assertThat(changed.text()).contains("11:00 – 11:30", "Antes:", "09:00", "/cita/" + token);
    }

    @Test
    void anyStaffCanBeChosenWhenRescheduling() {
        UUID luis = booking.createWorkingStaff(tenant.owner(), "Luis", haircut);
        booking.book(tenant.slug(), haircut, ana, at(day, "12:00"), AuthApi.unique("otro") + "@example.test");

        // A las 12:00 Ana está ocupada: con «cualquiera», la cita pasa a Luis.
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "12:00"), null)).hasStatus(HttpStatus.OK)
                .bodyJson().isLenientlyEqualTo("""
                        {"time": "12:00", "staffId": "%s", "staffName": "Luis"}""".formatted(luis));
    }

    @Test
    void takenOrNotOfferedSlotsAreConflicts() {
        booking.book(tenant.slug(), haircut, ana, at(day, "11:00"), AuthApi.unique("otro") + "@example.test");

        assertThat(booking.reschedule(tenant.slug(), token, at(day, "11:00"), ana)).hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "15:00"), ana))
                .as("fuera de horario").hasStatus(HttpStatus.CONFLICT);
        assertThat(booking.managed(tenant.slug(), token)).bodyJson().extractingPath("$.time").isEqualTo("09:00");
    }

    @Test
    void theSameTimeIsANoOp() {
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "09:00"), null)).hasStatus(HttpStatus.OK);

        assertThat(jdbc.sql("SELECT count(*) FROM email_outbox WHERE to_email = ?").param(email)
                .query(Long.class).single()).as("solo la confirmación").isEqualTo(1);
    }

    @Test
    void aCancelledAppointmentCannotBeRescheduled() {
        booking.cancel(tenant.slug(), token);

        assertThat(booking.reschedule(tenant.slug(), token, at(day, "10:00"), null)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo(ManageAppointmentService.CANCELLED);
    }

    @Test
    void invalidRescheduleRequestsAreRejected() {
        String url = "/api/v1/public/businesses/" + tenant.slug() + "/appointments/manage/" + token + "/reschedule";
        assertThat(mvc.post().uri(url).with(com.slotea.support.SpaCsrf.spaCsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
