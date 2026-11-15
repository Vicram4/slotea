package com.slotea.demo;

import static com.slotea.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;
import com.slotea.TestcontainersConfiguration;
import com.slotea.demo.DemoDtos.DemoSector;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Session;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;
import com.slotea.support.Mailpit;

/**
 * Demo en un clic: cada clic crea una copia aislada, se entra como dueño o empleado sin contraseña,
 * los emails solo se ven en la bandeja y las copias caducadas se borran.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DemoApiTests {

    static final String JOBS_TOKEN = "test-only-jobs-token-not-a-real-secret";
    static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

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

    @BeforeEach
    void helpers() {
        auth = new AuthApi(mvc, jdbc, passwordEncoder);
        catalog = new CatalogApi(mvc);
        booking = new BookingApi(mvc);
    }

    record Demo(UUID businessId, String slug, Session session, String body) {
    }

    @Test
    void oneClickOpensTheOwnerSessionOfAFreshCopy() {
        Demo demo = create("OWNER");

        assertThat(demo.slug()).matches("demo-[a-z0-9]{8}");
        assertThat((String) JsonPath.read(demo.body(), "$.activeBusiness.role")).isEqualTo("OWNER");
        assertThat((Boolean) JsonPath.read(demo.body(), "$.activeBusiness.demo")).isTrue();
        assertThat((Boolean) JsonPath.read(demo.body(), "$.activeBusiness.demoCanSwitch")).isTrue();
        assertThat((String) JsonPath.read(demo.body(), "$.activeBusiness.name")).isEqualTo(DemoTemplates.BARBERSHOP.businessName());
        assertThat((String) JsonPath.read(demo.body(), "$.user.fullName")).isEqualTo(DemoTemplates.BARBERSHOP.owner().name());

        // La agenda está llena: tres profesionales y citas ya empezadas que se pueden marcar.
        String agenda = AuthApi.body(agenda(demo.session()));
        List<String> staff = JsonPath.read(agenda, "$.staff[*].displayName");
        assertThat(staff).containsExactlyInAnyOrderElementsOf(
                DemoTemplates.BARBERSHOP.staff().stream().map(DemoTemplate.Staff::name).toList());
        List<Object> pending = JsonPath.read(agenda, "$.appointments[?(@.status == 'CONFIRMED' && @.canRecordOutcome)]");
        assertThat(pending).isNotEmpty();
        List<Object> upcoming = JsonPath.read(agenda, "$.appointments[?(@.canCancel)]");
        assertThat(upcoming).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(DemoSector.class)
    void everySectorOpensItsOwnBusiness(DemoSector sector) {
        DemoTemplate template = DemoTemplates.of(sector);
        Demo demo = create(sector, "OWNER");

        assertThat((String) JsonPath.read(demo.body(), "$.activeBusiness.name")).isEqualTo(template.businessName());
        assertThat((Boolean) JsonPath.read(demo.body(), "$.activeBusiness.demoCanSwitch"))
                .isEqualTo(template.employee().isPresent());
        String agenda = AuthApi.body(agenda(demo.session()));
        List<String> staff = JsonPath.read(agenda, "$.staff[*].displayName");
        assertThat(staff).hasSize(template.staff().size());
        // El dueño (que en psicología es la única profesional) tiene algo que marcar.
        List<Object> pending = JsonPath.read(agenda, "$.appointments[?(@.status == 'CONFIRMED' && @.canRecordOutcome)]");
        assertThat(pending).isNotEmpty();
        // Y su página pública deja reservar.
        bookFirstFreeSlot(demo.slug(), AuthApi.unique(sector.name().toLowerCase()) + "@example.test");
    }

    @Test
    void aSectorWithoutEmployeeHasNoStaffEntry() {
        MvcTestResult result = createResult(DemoSector.PSYCHOLOGY, "STAFF");
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(AuthApi.body(result)).contains("solo hay una profesional");

        // Tampoco se puede cambiar a empleado desde dentro: no hay cuenta a la que cambiar.
        Demo demo = create(DemoSector.PSYCHOLOGY, "OWNER");
        assertThat(catalog.send(demo.session(), "POST", "/api/v1/demo/switch", """
                {"role": "STAFF"}""")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void theEmployeeSeesOnlyTheirAgendaAndCanRecordOutcomes() {
        Demo demo = create("STAFF");
        assertThat((String) JsonPath.read(demo.body(), "$.activeBusiness.role")).isEqualTo("STAFF");

        String agenda = AuthApi.body(agenda(demo.session()));
        List<String> staffNames = JsonPath.read(agenda, "$.appointments[*].staffName");
        assertThat(staffNames).isNotEmpty().containsOnly(DemoTemplates.BARBERSHOP.employee().orElseThrow().name());
        List<String> unmarked = JsonPath.read(agenda,
                "$.appointments[?(@.status == 'CONFIRMED' && @.canRecordOutcome)].id");
        assertThat(unmarked).as("al menos una cita empezada sin marcar").isNotEmpty();

        assertThat(catalog.send(demo.session(), "PUT", "/api/v1/appointments/" + unmarked.getFirst() + "/status", """
                {"status": "COMPLETED"}""")).hasStatus(HttpStatus.OK);
    }

    @Test
    void switchingRolesStaysInTheSameCopy() {
        Demo demo = create("OWNER");

        MvcTestResult asStaff = catalog.send(demo.session(), "POST", "/api/v1/demo/switch", """
                {"role": "STAFF"}""");
        assertThat(asStaff).hasStatus(HttpStatus.OK);
        String body = AuthApi.body(asStaff);
        assertThat((String) JsonPath.read(body, "$.activeBusiness.role")).isEqualTo("STAFF");
        assertThat((String) JsonPath.read(body, "$.activeBusiness.businessId")).isEqualTo(demo.businessId().toString());
        assertThat((String) JsonPath.read(body, "$.user.fullName")).isEqualTo(DemoTemplates.BARBERSHOP.employee().orElseThrow().name());

        MvcTestResult back = catalog.send(AuthApi.sessionOf(asStaff), "POST", "/api/v1/demo/switch", """
                {"role": "OWNER"}""");
        assertThat(back).hasStatus(HttpStatus.OK);
        assertThat((String) JsonPath.read(AuthApi.body(back), "$.activeBusiness.role")).isEqualTo("OWNER");
    }

    @Test
    void aRealBusinessCannotUseTheDemoEndpoints() {
        var tenant = auth.signup("real");
        assertThat(catalog.send(tenant.owner(), "POST", "/api/v1/demo/switch", """
                {"role": "STAFF"}""")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(catalog.send(tenant.owner(), "GET", "/api/v1/demo/inbox", null)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat((Boolean) JsonPath.read(AuthApi.body(catalog.send(tenant.owner(), "GET", "/api/v1/me", null)), "$.activeBusiness.demoCanSwitch"))
                .isFalse();
    }

    @Test
    void switchingNeedsASessionAndCreatingNeedsCsrf() {
        assertThat(mvc.post().uri("/api/v1/demo/switch").with(spaCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"STAFF\"}")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/v1/demo/sessions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sector\": \"BARBERSHOP\", \"role\": \"OWNER\"}")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.post().uri("/api/v1/demo/sessions").with(spaCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sector\": \"BARBERSHOP\", \"role\": \"CUSTOMER\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        // El sector es obligatorio y tiene que ser uno de los que hay.
        assertThat(mvc.post().uri("/api/v1/demo/sessions").with(spaCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"OWNER\"}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/api/v1/demo/sessions").with(spaCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sector\": \"DENTIST\", \"role\": \"OWNER\"}")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void demoAccountsCannotLogInWithAPassword() {
        Demo demo = create("OWNER");
        String email = JsonPath.read(demo.body(), "$.user.email");
        assertThat(auth.loginResult(email, DemoSeeder.NO_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(auth.loginResult(email, "demo-account-without-password")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void realBusinessesCannotTakeTheDemoPrefix() {
        String slug = "demo-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(mvc.post().uri("/api/v1/auth/signup").with(spaCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content(AuthApi.signupJson("Falsa demo", slug, "Alguien", AuthApi.unique("x") + "@example.test")))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void aBookingOnTheCopyLandsInTheInboxAndIsNeverSent() {
        Demo demo = create("OWNER");
        String customer = AuthApi.unique("recruiter") + "@example.test";
        String token = bookFirstFreeSlot(demo.slug(), customer);

        String inbox = AuthApi.body(catalog.send(demo.session(), "GET", "/api/v1/demo/inbox", null));
        List<String> types = JsonPath.read(inbox, "$[*].type");
        assertThat(types).containsExactlyInAnyOrder("BOOKING_CONFIRMED", "BUSINESS_NEW_BOOKING");
        List<String> subjects = JsonPath.read(inbox, "$[?(@.toBusiness == false)].subject");
        assertThat(subjects).singleElement().asString().startsWith("Cita confirmada:");
        assertThat(mailpit.to(customer)).isEmpty();

        // El detalle es el email tal cual lo recibiría el cliente, con su enlace de gestión.
        List<String> customerEmail = JsonPath.read(inbox, "$[?(@.toBusiness == false)].id");
        MvcTestResult detail = catalog.send(demo.session(), "GET", "/api/v1/demo/inbox/" + customerEmail.getFirst(), null);
        assertThat(detail).hasStatus(HttpStatus.OK);
        assertThat((String) JsonPath.read(AuthApi.body(detail), "$.html")).contains("/" + demo.slug() + "/cita/" + token);
        assertThat((String) JsonPath.read(AuthApi.body(detail), "$.to")).isEqualTo(customer);
    }

    @Test
    void theInboxIsForTheOwnerOnly() {
        Demo demo = create("STAFF");
        assertThat(catalog.send(demo.session(), "GET", "/api/v1/demo/inbox", null)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void everyClickGetsAnIsolatedCopy() {
        Demo a = create("OWNER");
        Demo b = create("OWNER");
        assertThat(a.businessId()).isNotEqualTo(b.businessId());
        bookFirstFreeSlot(b.slug(), AuthApi.unique("b") + "@example.test");

        // A no ve ni la cita ni los emails de B (404, como cualquier recurso de otro negocio).
        String bAgenda = AuthApi.body(agenda(b.session()));
        String bAppointment = JsonPath.<List<String>>read(bAgenda, "$.appointments[*].id").getFirst();
        assertThat(catalog.send(a.session(), "GET", "/api/v1/appointments/" + bAppointment, null))
                .hasStatus(HttpStatus.NOT_FOUND);
        String bEmail = JsonPath.<List<String>>read(
                AuthApi.body(catalog.send(b.session(), "GET", "/api/v1/demo/inbox", null)), "$[*].id").getFirst();
        assertThat(catalog.send(a.session(), "GET", "/api/v1/demo/inbox/" + bEmail, null))
                .hasStatus(HttpStatus.NOT_FOUND);
        List<Object> aInbox = JsonPath.read(AuthApi.body(catalog.send(a.session(), "GET", "/api/v1/demo/inbox", null)),
                "$[*]");
        assertThat(aInbox).isEmpty();
    }

    @Test
    void theCleanupJobDeletesExpiredCopiesWithTheirUsers() {
        Demo expired = create("OWNER");
        Demo fresh = create("OWNER");
        jdbc.sql("UPDATE business SET created_at = ? WHERE id = ?")
                .params(Timestamp.from(Instant.now().minus(DemoService.TTL).minus(Duration.ofMinutes(5))),
                        expired.businessId())
                .update();
        String expiredOwnerEmail = JsonPath.read(expired.body(), "$.user.email");

        assertThat(mvc.post().uri("/internal/jobs/demo-cleanup").header("X-Jobs-Token", JOBS_TOKEN))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.deleted").asNumber().isNotEqualTo(0);

        assertThat(count("SELECT count(*) FROM business WHERE id = ?", expired.businessId())).isZero();
        assertThat(count("SELECT count(*) FROM appointment WHERE business_id = ?", expired.businessId())).isZero();
        assertThat(count("SELECT count(*) FROM app_user WHERE email = ?", expiredOwnerEmail)).isZero();
        assertThat(count("SELECT count(*) FROM business WHERE id = ?", fresh.businessId())).isOne();
        // Su sesión ya no sirve: el usuario no existe.
        assertThat(auth.refresh(expired.session().refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theCleanupJobNeedsTheToken() {
        assertThat(mvc.post().uri("/internal/jobs/demo-cleanup")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tooManyLiveCopiesGive503() {
        long live = count("SELECT count(*) FROM business WHERE is_demo AND created_at > now() - interval '24 hours'");
        List<UUID> fillers = new ArrayList<>();
        try {
            for (long i = live; i < DemoService.MAX_LIVE_COPIES; i++) {
                UUID id = UUID.randomUUID();
                fillers.add(id);
                jdbc.sql("""
                        INSERT INTO business (id, slug, name, contact_email, is_demo)
                        VALUES (?, ?, 'Relleno', 'relleno@example.com', true)""")
                        .params(id, "filler-" + id.toString().substring(0, 13))
                        .update();
            }
            MvcTestResult result = createResult(DemoSector.BARBERSHOP, "OWNER");
            assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(AuthApi.body(result)).contains("La demo está llena");
        } finally {
            fillers.forEach(id -> jdbc.sql("DELETE FROM business WHERE id = ?").param(id).update());
        }
    }

    // --- Utilidades ------------------------------------------------------------------------------

    private Demo create(String role) {
        return create(DemoSector.BARBERSHOP, role);
    }

    private Demo create(DemoSector sector, String role) {
        MvcTestResult result = createResult(sector, role);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = AuthApi.body(result);
        return new Demo(UUID.fromString(JsonPath.read(body, "$.activeBusiness.businessId")),
                JsonPath.read(body, "$.activeBusiness.slug"), AuthApi.sessionOf(result), body);
    }

    private MvcTestResult createResult(DemoSector sector, String role) {
        return mvc.post().uri("/api/v1/demo/sessions").with(spaCsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sector\": \"" + sector + "\", \"role\": \"" + role + "\"}").exchange();
    }

    private MvcTestResult agenda(Session session) {
        LocalDate today = LocalDate.now(MADRID);
        MvcTestResult result = catalog.send(session, "GET", "/api/v1/appointments?from="
                + today.minusDays(DemoSeeder.DAYS_BACK) + "&to=" + today.plusDays(DemoSeeder.DAYS_AHEAD), null);
        assertThat(result).hasStatus(HttpStatus.OK);
        return result;
    }

    /** Reserva como un visitante el primer hueco libre de la copia y devuelve el token del enlace. */
    private String bookFirstFreeSlot(String slug, String email) {
        String business = AuthApi.body(booking.business(slug));
        UUID serviceId = UUID.fromString(JsonPath.<List<String>>read(business, "$.services[*].id").getFirst());
        LocalDate today = LocalDate.now(MADRID);
        String availability = AuthApi.body(booking.availability(slug, serviceId, null, today.plusDays(1),
                today.plusDays(10)));
        List<String> slots = JsonPath.read(availability, "$.days[*].slots[*].startsAt");
        assertThat(slots).as("la demo deja huecos libres").isNotEmpty();
        return booking.bookForToken(slug, serviceId, null, Instant.parse(slots.getFirst()), email);
    }

    private long count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }
}
