package com.slotea.booking;

import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;
import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Session;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;
import com.slotea.support.Mailpit;

/**
 * La agenda del panel: qué ve el dueño, qué ve un empleado (solo lo suyo), los días en la zona del
 * negocio y las acciones (marcar cómo acabó la cita y cancelarla).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AgendaApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Mailpit mailpit;

    AuthApi auth;
    CatalogApi catalog;
    BookingApi booking;
    Tenant tenant;
    UUID haircut;
    UUID ana;
    UUID bruno;
    LocalDate day;
    UUID anaAppointment;
    UUID brunoAppointment;
    String anaCustomer;

    @BeforeEach
    void businessWithTwoAppointments() {
        auth = new AuthApi(mvc, jdbc, passwordEncoder);
        catalog = new CatalogApi(mvc);
        booking = new BookingApi(mvc);
        tenant = auth.signup("agenda");
        haircut = catalog.createService(tenant.owner(), "Corte");
        ana = booking.createWorkingStaff(tenant.owner(), "Ana", haircut);
        bruno = booking.createWorkingStaff(tenant.owner(), "Bruno", haircut);
        day = aWeekFromNow();
        anaCustomer = AuthApi.unique("ana-client") + "@example.test";
        anaAppointment = book(ana, "09:00", anaCustomer);
        brunoAppointment = book(bruno, "10:00", AuthApi.unique("bruno-client") + "@example.test");
    }

    // --- Ver la agenda ---------------------------------------------------------------------------

    @Test
    void theOwnerSeesEveryAppointmentOfTheDayWithItsDetails() {
        assertThat(agenda(tenant.owner(), day, day, null)).hasStatus(HttpStatus.OK).bodyJson()
                .isLenientlyEqualTo("""
                        {"timezone": "Europe/Madrid", "from": "%s", "to": "%s",
                         "staff": [{"displayName": "Ana", "active": true}, {"displayName": "Bruno", "active": true}],
                         "appointments": [
                           {"id": "%s", "status": "CONFIRMED", "staffName": "Ana", "serviceName": "Corte",
                            "customer": {"fullName": "Cliente de prueba", "email": "%s", "phone": "+34 600 000 000"},
                            "date": "%s", "startTime": "09:00", "endTime": "09:30",
                            "canCancel": true, "canRecordOutcome": false},
                           {"id": "%s", "staffName": "Bruno", "startTime": "10:00"}
                         ]}""".formatted(day, day, anaAppointment, anaCustomer, day, brunoAppointment));
    }

    @Test
    void theOwnerCanLookAtOneProfessional() {
        assertThat(agenda(tenant.owner(), day, day, bruno)).bodyJson()
                .extractingPath("$.appointments[*].id").asArray().containsExactly(brunoAppointment.toString());
        assertThat(agenda(tenant.owner(), day, day, UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelledAppointmentsStayInTheAgendaAndInactiveStaffWithAppointmentsKeepTheirColumn() {
        assertThat(cancel(tenant.owner(), brunoAppointment)).hasStatus(HttpStatus.OK);
        assertThat(catalog.send(tenant.owner(), "PUT", "/api/v1/staff/" + bruno, """
                {"displayName": "Bruno", "active": false}""")).hasStatus(HttpStatus.OK);

        var body = assertThat(agenda(tenant.owner(), day, day, null)).bodyJson();
        body.extractingPath("$.appointments[1].status").isEqualTo("CANCELLED");
        body.extractingPath("$.staff[*].active").asArray().containsExactly(true, false);
        // Otro día sin citas suyas: un profesional inactivo ya no ocupa columna.
        assertThat(agenda(tenant.owner(), day.plusDays(1), day.plusDays(1), null)).bodyJson()
                .extractingPath("$.staff[*].displayName").asArray().containsExactly("Ana");
    }

    @Test
    void anEmployeeOnlySeesTheirOwnAppointments() {
        Session anaUser = linkedEmployee(ana);

        var body = assertThat(agenda(anaUser, day, day, null)).hasStatus(HttpStatus.OK).bodyJson();
        body.extractingPath("$.appointments[*].id").asArray().containsExactly(anaAppointment.toString());
        body.extractingPath("$.staff[*].displayName").asArray().containsExactly("Ana");

        assertThat(agenda(anaUser, day, day, ana)).hasStatus(HttpStatus.OK);
        assertThat(agenda(anaUser, day, day, bruno)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(anaUser, anaAppointment)).hasStatus(HttpStatus.OK);
    }

    @Test
    void anEmployeeCannotSeeOrChangeAnotherProfessionalsAppointment() {
        Session anaUser = linkedEmployee(ana);
        movePast(brunoAppointment);

        // 404, como si no existiera (igual que la cita de otro negocio).
        assertThat(get(anaUser, brunoAppointment)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(status(anaUser, brunoAppointment, "NO_SHOW")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(cancel(anaUser, brunoAppointment)).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(get(tenant.owner(), brunoAppointment)).bodyJson().extractingPath("$.status")
                .isEqualTo("CONFIRMED");
    }

    @Test
    void anEmployeeWithoutAProfessionalProfileHasAnEmptyAgenda() {
        Session unlinked = auth.addStaff(tenant, "sin-ficha");

        assertThat(agenda(unlinked, day, day, null)).hasStatus(HttpStatus.OK).bodyJson()
                .isLenientlyEqualTo("""
                        {"staff": [], "appointments": []}""");
        assertThat(agenda(unlinked, day, day, ana)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(unlinked, anaAppointment)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theRangeIsValidated() {
        assertThat(agenda(tenant.owner(), day, day.minusDays(1), null)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(agenda(tenant.owner(), day, day.plusDays(AgendaService.MAX_DAYS - 1), null))
                .hasStatus(HttpStatus.OK);
        assertThat(agenda(tenant.owner(), day, day.plusDays(AgendaService.MAX_DAYS), null))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/appointments?from=" + day).cookie(tenant.owner().access()).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theAgendaNeedsASession() {
        assertThat(mvc.get().uri("/api/v1/appointments?from=" + day + "&to=" + day).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void daysAreCountedInTheBusinessTimeZoneEvenWhenTheClocksChange() {
        // En 2026, Madrid vuelve al horario de invierno el domingo 25 de octubre: ese día dura 25 horas.
        LocalDate dstEnd = LocalDate.of(2026, 10, 25);
        UUID beforeMidnight = insert(ana, "2026-10-24T21:30:00Z"); // sábado 23:30 (+02:00)
        UUID midnight = insert(ana, "2026-10-24T22:00:00Z"); // domingo 00:00 (+02:00)
        UUID lastHalfHour = insert(ana, "2026-10-25T22:30:00Z"); // domingo 23:30 (+01:00)
        UUID nextDay = insert(ana, "2026-10-25T23:00:00Z"); // lunes 00:00 (+01:00)

        var body = assertThat(agenda(tenant.owner(), dstEnd, dstEnd, null)).bodyJson();
        body.extractingPath("$.appointments[*].id").asArray()
                .containsExactly(midnight.toString(), lastHalfHour.toString());
        body.extractingPath("$.appointments[*].startTime").asArray().containsExactly("00:00", "23:30");

        assertThat(agenda(tenant.owner(), dstEnd.minusDays(1), dstEnd.plusDays(1), null)).bodyJson()
                .extractingPath("$.appointments[*].id").asArray()
                .containsExactly(beforeMidnight.toString(), midnight.toString(), lastHalfHour.toString(),
                        nextDay.toString());
    }

    // --- Marcar cómo acabó -----------------------------------------------------------------------

    @Test
    void anAppointmentThatHasStartedCanBeMarkedAndCorrected() {
        movePast(anaAppointment);
        Session anaUser = linkedEmployee(ana);

        assertThat(get(anaUser, anaAppointment)).bodyJson().isLenientlyEqualTo("""
                {"canCancel": false, "canRecordOutcome": true}""");
        assertThat(status(anaUser, anaAppointment, "COMPLETED")).hasStatus(HttpStatus.OK).bodyJson()
                .extractingPath("$.status").isEqualTo("COMPLETED");
        assertThat(status(tenant.owner(), anaAppointment, "NO_SHOW")).bodyJson()
                .extractingPath("$.status").isEqualTo("NO_SHOW");
        assertThat(status(tenant.owner(), anaAppointment, "NO_SHOW")).hasStatus(HttpStatus.OK);
        assertThat(status(tenant.owner(), anaAppointment, "CONFIRMED")).bodyJson()
                .extractingPath("$.status").isEqualTo("CONFIRMED");
        assertThat(dbStatus(anaAppointment)).isEqualTo("CONFIRMED");
    }

    @Test
    void aFutureAppointmentCannotBeMarked() {
        assertThat(status(tenant.owner(), anaAppointment, "NO_SHOW")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo(AgendaService.NOT_STARTED);
        assertThat(dbStatus(anaAppointment)).isEqualTo("CONFIRMED");
    }

    @Test
    void cancellingIsNotAnOutcomeAndACancelledAppointmentCannotBeMarked() {
        assertThat(status(tenant.owner(), anaAppointment, "CANCELLED")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(status(tenant.owner(), anaAppointment, "PAID")).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(cancel(tenant.owner(), anaAppointment)).hasStatus(HttpStatus.OK);
        movePast(anaAppointment);
        assertThat(status(tenant.owner(), anaAppointment, "COMPLETED")).hasStatus(HttpStatus.CONFLICT);
    }

    // --- Cancelar desde el panel -----------------------------------------------------------------

    @Test
    void cancellingFromThePanelFreesTheSlotAndEmailsTheCustomerOnlyOnce() {
        assertThat(cancel(tenant.owner(), anaAppointment)).hasStatus(HttpStatus.OK).bodyJson()
                .isLenientlyEqualTo("""
                        {"status": "CANCELLED", "canCancel": false, "canRecordOutcome": false}""");
        assertThat(cancel(tenant.owner(), anaAppointment)).hasStatus(HttpStatus.OK);

        assertThat(mailpit.await(anaCustomer, 2)).extracting(Mailpit.Message::subject)
                .containsExactly("Cita cancelada: Corte, " + subjectDay() + " a las 09:00",
                        "Cita confirmada: Corte, " + subjectDay() + " a las 09:00");
        // El negocio no recibe aviso de lo que acaba de hacer él.
        long notices = jdbc.sql("""
                SELECT count(*) FROM email_outbox WHERE appointment_id = ? AND type = 'BUSINESS_BOOKING_CANCELLED'""")
                .param(anaAppointment).query(Long.class).single();
        assertThat(notices).isZero();
        assertThat(booking.book(tenant.slug(), haircut, ana, at(day, "09:00"), AuthApi.unique("x") + "@example.test"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void anEmployeeCanCancelTheirOwnAppointments() {
        assertThat(cancel(linkedEmployee(ana), anaAppointment)).hasStatus(HttpStatus.OK);
        assertThat(dbStatus(anaAppointment)).isEqualTo("CANCELLED");
    }

    @Test
    void startedOrFinishedAppointmentsCannotBeCancelled() {
        movePast(anaAppointment);
        assertThat(cancel(tenant.owner(), anaAppointment)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo(AgendaService.ALREADY_STARTED);

        assertThat(status(tenant.owner(), anaAppointment, "COMPLETED")).hasStatus(HttpStatus.OK);
        assertThat(cancel(tenant.owner(), anaAppointment)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo(AgendaService.FINISHED);
    }

    @Test
    void changesNeedTheCsrfToken() {
        assertThat(mvc.post().uri("/api/v1/appointments/" + anaAppointment + "/cancel")
                .cookie(tenant.owner().access()).exchange()).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(dbStatus(anaAppointment)).isEqualTo("CONFIRMED");
    }

    // --- Avisos al negocio -----------------------------------------------------------------------

    @Test
    void theBusinessIsToldAboutEveryChangeTheCustomerMakes() {
        String customer = AuthApi.unique("aviso") + "@example.test";
        String token = booking.bookForToken(tenant.slug(), haircut, ana, at(day, "11:00"), customer);
        assertThat(booking.reschedule(tenant.slug(), token, at(day, "12:00"), null)).hasStatus(HttpStatus.OK);
        assertThat(booking.cancel(tenant.slug(), token)).hasStatus(HttpStatus.OK);

        var notices = mailpit.await(tenant.ownerEmail(), 5).stream()
                .filter(m -> m.text().contains(customer))
                .toList();
        assertThat(notices).extracting(Mailpit.Message::subject).containsExactly(
                "Cita cancelada: Cliente de prueba, " + subjectDay() + " a las 12:00",
                "Cita cambiada: Cliente de prueba, ahora el " + subjectDay() + " a las 12:00",
                "Nueva reserva: Cliente de prueba, " + subjectDay() + " a las 11:00");
        // Si el negocio responde, le escribe al cliente; y nunca recibe el enlace del cliente.
        assertThat(notices).allSatisfy(notice -> {
            assertThat(notice.replyTo()).containsExactly(customer);
            assertThat(notice.text()).contains("/panel/agenda?date=" + day).doesNotContain(token);
        });
    }

    @Test
    void demoBusinessesGetNoNotices() {
        jdbc.sql("UPDATE business SET is_demo = true WHERE id = ?").param(tenant.businessId()).update();
        String customer = AuthApi.unique("demo") + "@example.test";
        booking.bookForToken(tenant.slug(), haircut, ana, at(day, "11:00"), customer);

        mailpit.awaitOne(customer); // el cliente sí recibe su confirmación
        long notices = jdbc.sql("""
                SELECT count(*) FROM email_outbox WHERE business_id = ? AND type LIKE 'BUSINESS_%'
                  AND payload ->> 'customerEmail' = ?""")
                .params(tenant.businessId(), customer).query(Long.class).single();
        assertThat(notices).isZero();
    }

    // --- Utilidades ------------------------------------------------------------------------------

    private UUID book(UUID staffId, String time, String email) {
        MvcTestResult result = booking.book(tenant.slug(), haircut, staffId, at(day, time), email);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(AuthApi.body(result), "$.id"));
    }

    private Session linkedEmployee(UUID staffId) {
        var user = auth.addStaffUser(tenant, "empleado");
        assertThat(catalog.send(tenant.owner(), "PUT", "/api/v1/staff/" + staffId, """
                {"displayName": "Ana", "userId": "%s"}""".formatted(user.userId()))).hasStatus(HttpStatus.OK);
        return user.session();
    }

    private MvcTestResult agenda(Session session, LocalDate from, LocalDate to, @Nullable UUID staffId) {
        String staff = staffId == null ? "" : "&staffId=" + staffId;
        return catalog.send(session, "GET", "/api/v1/appointments?from=" + from + "&to=" + to + staff, null);
    }

    private MvcTestResult get(Session session, UUID id) {
        return catalog.send(session, "GET", "/api/v1/appointments/" + id, null);
    }

    private MvcTestResult status(Session session, UUID id, String status) {
        return catalog.send(session, "PUT", "/api/v1/appointments/" + id + "/status", """
                {"status": "%s"}""".formatted(status));
    }

    private MvcTestResult cancel(Session session, UUID id) {
        return catalog.send(session, "POST", "/api/v1/appointments/" + id + "/cancel", null);
    }

    /** La cita empezó hace una hora (las reservas solo se pueden hacer en el futuro). */
    private void movePast(UUID id) {
        Instant start = Instant.now().minus(Duration.ofHours(1));
        jdbc.sql("UPDATE appointment SET starts_at = ?, ends_at = ?, blocked_until = ? WHERE id = ?")
                .params(Timestamp.from(start), Timestamp.from(start.plus(Duration.ofMinutes(30))),
                        Timestamp.from(start.plus(Duration.ofMinutes(35))), id)
                .update();
    }

    /** Una cita en un instante cualquiera (fuera del horizonte de reservas), directamente en la BD. */
    private UUID insert(UUID staffId, String startsAt) {
        Instant start = Instant.parse(startsAt);
        UUID customer = UUID.randomUUID();
        jdbc.sql("INSERT INTO customer (id, business_id, email, full_name) VALUES (?, ?, ?, 'Cliente directo')")
                .params(customer, tenant.businessId(), AuthApi.unique("directo") + "@example.test")
                .update();
        UUID id = UUID.randomUUID();
        byte[] tokenHash = new byte[32];
        new SecureRandom().nextBytes(tokenHash);
        jdbc.sql("""
                INSERT INTO appointment (id, business_id, staff_id, service_id, customer_id, starts_at, ends_at,
                                         blocked_until, manage_token_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(id, tenant.businessId(), staffId, haircut, customer, Timestamp.from(start),
                        Timestamp.from(start.plus(Duration.ofMinutes(25))),
                        Timestamp.from(start.plus(Duration.ofMinutes(30))), tokenHash)
                .update();
        return id;
    }

    private String dbStatus(UUID id) {
        return jdbc.sql("SELECT status FROM appointment WHERE id = ?").param(id).query(String.class).single();
    }

    private String subjectDay() {
        return java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale.of("es", "ES"))
                .format(day);
    }
}
