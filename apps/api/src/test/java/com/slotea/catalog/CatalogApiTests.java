package com.slotea.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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

import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.CatalogApi;

/** El catálogo de punta a punta (HTTP → BD real): servicios, profesionales, horarios y cierres. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogApiTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    CatalogApi api;
    Tenant tenant;

    @BeforeEach
    void newBusiness() {
        api = new CatalogApi(mvc);
        tenant = new AuthApi(mvc, jdbc, passwordEncoder).signup("catalog");
    }

    // --- Servicios -------------------------------------------------------------------------------

    @Test
    void createsListsAndUpdatesServices() {
        var created = api.send(tenant.owner(), "POST", "/api/v1/services", """
                {"name": "  Corte y barba ", "description": "Con toalla caliente", "durationMin": 45,
                 "bufferMin": 10, "priceCents": 2500}""");
        assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
                {"name": "Corte y barba", "description": "Con toalla caliente", "durationMin": 45,
                 "bufferMin": 10, "priceCents": 2500, "active": true}""");
        String id = com.jayway.jsonpath.JsonPath.read(AuthApi.body(created), "$.id");
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/services/" + id);

        // PUT completo: sin precio ni descripción → se quitan. active=false → desactivado.
        assertThat(api.send(tenant.owner(), "PUT", "/api/v1/services/" + id, """
                {"name": "Corte y barba", "durationMin": 40, "active": false}"""))
                .hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
                        {"description": null, "priceCents": null, "bufferMin": 0, "durationMin": 40,
                         "active": false}""");

        api.createService(tenant.owner(), "Afeitado");
        // Activos primero, luego por nombre.
        assertThat(api.send(tenant.owner(), "GET", "/api/v1/services", null))
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Afeitado", "Corte y barba");
    }

    @Test
    void invalidServicesAreRejectedFieldByField() {
        assertThat(api.send(tenant.owner(), "POST", "/api/v1/services", """
                {"name": " ", "durationMin": 32, "bufferMin": -5, "priceCents": -1}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray()
                .contains("name", "durationMin", "bufferMin", "priceCents");
    }

    @Test
    void duplicateServiceNameIsAConflict() {
        api.createService(tenant.owner(), "Corte");
        assertThat(api.send(tenant.owner(), "POST", "/api/v1/services", """
                {"name": "CORTE", "durationMin": 30}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Ya tienes un servicio con ese nombre.");
    }

    // --- Profesionales ---------------------------------------------------------------------------

    @Test
    void ownerCanAddThemselvesAsStaffOnlyOnce() {
        UUID me = api.createStaff(tenant.owner(), "Yo mismo", tenant.ownerId());
        assertThat(api.send(tenant.owner(), "GET", "/api/v1/staff/" + me, null))
                .bodyJson().isLenientlyEqualTo("""
                        {"displayName": "Yo mismo", "userId": "%s", "active": true, "serviceIds": []}"""
                        .formatted(tenant.ownerId()));

        assertThat(api.send(tenant.owner(), "POST", "/api/v1/staff", """
                {"displayName": "Otra vez yo", "userId": "%s"}""".formatted(tenant.ownerId())))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Ese usuario ya tiene ficha de profesional.");
    }

    @Test
    void assignsServicesToStaff() {
        UUID corte = api.createService(tenant.owner(), "Corte");
        UUID barba = api.createService(tenant.owner(), "Barba");
        UUID ana = api.createStaff(tenant.owner(), "Ana", null);

        assertThat(api.send(tenant.owner(), "PUT", "/api/v1/staff/%s/services".formatted(ana), """
                {"serviceIds": ["%s", "%s", "%s"]}""".formatted(corte, barba, corte)))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.serviceIds").asArray()
                .containsExactly(barba.toString(), corte.toString()); // sin duplicados, por nombre

        assertThat(api.send(tenant.owner(), "PUT", "/api/v1/staff/%s/services".formatted(ana), """
                {"serviceIds": ["%s"]}""".formatted(barba)))
                .bodyJson().extractingPath("$.serviceIds").asArray().containsExactly(barba.toString());

        assertThat(api.send(tenant.owner(), "GET", "/api/v1/staff", null))
                .bodyJson().extractingPath("$[0].serviceIds").asArray().containsExactly(barba.toString());

        assertThat(api.send(tenant.owner(), "PUT", "/api/v1/staff/%s/services".formatted(ana), """
                {"serviceIds": ["%s"]}""".formatted(UUID.randomUUID())))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deactivatesStaffInsteadOfDeleting() {
        UUID ana = api.createStaff(tenant.owner(), "Ana", null);
        assertThat(api.send(tenant.owner(), "PUT", "/api/v1/staff/" + ana, """
                {"displayName": "Ana G.", "active": false}"""))
                .hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
                        {"displayName": "Ana G.", "active": false, "userId": null}""");
    }

    // --- Horario semanal -------------------------------------------------------------------------

    @Test
    void replacesTheWeeklyHoursWithASplitShift() {
        UUID ana = api.createStaff(tenant.owner(), "Ana", null);
        String uri = "/api/v1/staff/%s/working-hours".formatted(ana);

        assertThat(api.send(tenant.owner(), "PUT", uri, """
                {"slots": [
                  {"dayOfWeek": 2, "start": "10:00", "end": "14:00"},
                  {"dayOfWeek": 1, "start": "16:00", "end": "20:00"},
                  {"dayOfWeek": 1, "start": "09:30", "end": "16:00"}
                ]}"""))
                .hasStatus(HttpStatus.OK).bodyJson().isStrictlyEqualTo("""
                        {"slots": [
                          {"dayOfWeek": 1, "start": "09:30", "end": "16:00"},
                          {"dayOfWeek": 1, "start": "16:00", "end": "20:00"},
                          {"dayOfWeek": 2, "start": "10:00", "end": "14:00"}
                        ]}""");

        // Sustituir por un horario que se solaparía con el anterior (si no se borrara antes) funciona.
        assertThat(api.send(tenant.owner(), "PUT", uri, """
                {"slots": [{"dayOfWeek": 1, "start": "08:00", "end": "21:00"}]}"""))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.slots").asArray().hasSize(1);

        assertThat(api.send(tenant.owner(), "PUT", uri, "{\"slots\": []}"))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.slots").asArray().isEmpty();
    }

    @Test
    void invalidWeeklyHoursPointToTheWrongSlot() {
        UUID ana = api.createStaff(tenant.owner(), "Ana", null);
        String uri = "/api/v1/staff/%s/working-hours".formatted(ana);

        assertThat(api.send(tenant.owner(), "PUT", uri, """
                {"slots": [
                  {"dayOfWeek": 1, "start": "09:00", "end": "14:00"},
                  {"dayOfWeek": 1, "start": "13:00", "end": "18:00"}
                ]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0]").isEqualTo(java.util.Map.of(
                        "field", "slots[1].start", "message", "se solapa con otro tramo del mismo día"));

        assertThat(api.send(tenant.owner(), "PUT", uri, """
                {"slots": [{"dayOfWeek": 8, "start": "09:00", "end": "14:00"}]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[*].field").asArray().contains("slots[0].dayOfWeek");

        assertThat(api.send(tenant.owner(), "PUT", uri, """
                {"slots": [{"dayOfWeek": 3, "start": "18:00", "end": "09:00"}]}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("slots[0].end");

        // Nada de lo anterior se ha guardado.
        assertThat(api.send(tenant.owner(), "GET", uri, null))
                .bodyJson().extractingPath("$.slots").asArray().isEmpty();
    }

    // --- Cierres ---------------------------------------------------------------------------------

    @Test
    void createsFiltersAndDeletesClosures() {
        UUID ana = api.createStaff(tenant.owner(), "Ana", null);
        UUID christmas = api.createClosure(tenant.owner(), null, "2026-12-24", "2026-12-26");
        UUID holidays = api.createClosure(tenant.owner(), ana, "2027-08-01", "2027-08-15");

        assertThat(api.send(tenant.owner(), "GET", "/api/v1/closures", null))
                .bodyJson().extractingPath("$[*].id").asArray()
                .containsExactly(christmas.toString(), holidays.toString());
        // Toca el periodo aunque empiece antes (fechas inclusivas).
        assertThat(api.send(tenant.owner(), "GET", "/api/v1/closures?from=2026-12-26&to=2027-01-31", null))
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(christmas.toString());
        assertThat(api.send(tenant.owner(), "GET", "/api/v1/closures?from=2026-12-27", null))
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(holidays.toString());
        assertThat(api.send(tenant.owner(), "GET", "/api/v1/closures?staffId=" + ana, null))
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(holidays.toString());

        assertThat(api.send(tenant.owner(), "DELETE", "/api/v1/closures/" + christmas, null))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(api.send(tenant.owner(), "DELETE", "/api/v1/closures/" + christmas, null))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void closureMustEndOnOrAfterItsStart() {
        assertThat(api.send(tenant.owner(), "POST", "/api/v1/closures", """
                {"startDate": "2026-12-26", "endDate": "2026-12-24"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors[0].field").isEqualTo("endDate");
        assertThat(api.send(tenant.owner(), "POST", "/api/v1/closures", """
                {"startDate": "2026-01-01", "endDate": "2028-01-01"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
