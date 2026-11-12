package com.slotea.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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

import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Session;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.CatalogApi;

/**
 * Regla del proyecto, aplicada al catálogo: el negocio A no ve ni toca nada del negocio B.
 *
 * <p>{@link #ENDPOINTS} es la lista de TODOS los endpoints del catálogo que reciben un id. Cada uno se
 * prueba con ids del negocio B (en la ruta o en el cuerpo) → 404, nunca 403 (no revelamos que existen).
 * Al añadir un endpoint, se añade aquí su fila.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogIsolationTests {

    /**
     * Un endpoint con marcadores que se sustituyen por ids reales: {@code {service}}, {@code {staff}},
     * {@code {closure}}, {@code {member}} (de B) y {@code {ownStaff}} (de A).
     */
    record Endpoint(String method, String uri, @Nullable String body) {

        @Override
        public String toString() {
            return method + " " + uri + (body == null ? "" : " " + body.strip());
        }
    }

    static final String SERVICE_BODY = """
            {"name": "Robado", "durationMin": 30}""";
    static final String STAFF_BODY = """
            {"displayName": "Robado"}""";

    static Stream<Endpoint> ENDPOINTS() {
        return Stream.of(
                // Ids de B en la ruta.
                new Endpoint("GET", "/api/v1/services/{service}", null),
                new Endpoint("PUT", "/api/v1/services/{service}", SERVICE_BODY),
                new Endpoint("GET", "/api/v1/staff/{staff}", null),
                new Endpoint("PUT", "/api/v1/staff/{staff}", STAFF_BODY),
                new Endpoint("PUT", "/api/v1/staff/{staff}/services", """
                        {"serviceIds": []}"""),
                new Endpoint("GET", "/api/v1/staff/{staff}/working-hours", null),
                new Endpoint("PUT", "/api/v1/staff/{staff}/working-hours", """
                        {"slots": [{"dayOfWeek": 1, "start": "09:00", "end": "14:00"}]}"""),
                new Endpoint("GET", "/api/v1/closures?staffId={staff}", null),
                new Endpoint("DELETE", "/api/v1/closures/{closure}", null),
                // Ids de B dentro del cuerpo, sobre recursos propios de A.
                new Endpoint("PUT", "/api/v1/staff/{ownStaff}/services", """
                        {"serviceIds": ["{service}"]}"""),
                new Endpoint("POST", "/api/v1/staff", """
                        {"displayName": "Intruso", "userId": "{member}"}"""),
                new Endpoint("PUT", "/api/v1/staff/{ownStaff}", """
                        {"displayName": "Intruso", "userId": "{member}"}"""),
                new Endpoint("POST", "/api/v1/closures", """
                        {"staffId": "{staff}", "startDate": "2026-12-24", "endDate": "2026-12-26"}"""));
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
    UUID ownStaffA;
    UUID serviceB;
    UUID staffB;
    UUID closureB;

    @BeforeEach
    void twoTenantsWithCatalog() {
        var auth = new AuthApi(mvc, jdbc, passwordEncoder);
        catalog = new CatalogApi(mvc);
        a = auth.signup("cat-a");
        b = auth.signup("cat-b");
        ownStaffA = catalog.createStaff(a.owner(), "Ana", null);
        catalog.createService(a.owner(), "Corte A");
        serviceB = catalog.createService(b.owner(), "Corte B");
        staffB = catalog.createStaff(b.owner(), "Berta", b.ownerId());
        closureB = catalog.createClosure(b.owner(), staffB, "2026-12-24", "2026-12-26");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ENDPOINTS")
    void idsOfAnotherBusinessAreNotFound(Endpoint endpoint) {
        Map<String, String> ids = Map.of("{service}", serviceB.toString(), "{staff}", staffB.toString(),
                "{closure}", closureB.toString(), "{member}", b.ownerId().toString(),
                "{ownStaff}", ownStaffA.toString());
        String uri = endpoint.uri();
        String body = endpoint.body();
        for (var id : ids.entrySet()) {
            uri = uri.replace(id.getKey(), id.getValue());
            body = body == null ? null : body.replace(id.getKey(), id.getValue());
        }

        assertThat(catalog.send(a.owner(), endpoint.method(), uri, body))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);

        assertBusinessBIsUntouched();
    }

    @Test
    void listsOnlyContainTheCurrentBusiness() {
        assertThat(catalog.send(a.owner(), "GET", "/api/v1/services", null))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$[*].name").asArray().containsExactly("Corte A");
        assertThat(catalog.send(a.owner(), "GET", "/api/v1/staff", null))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$[*].displayName").asArray()
                .containsExactly("Ana");
        assertThat(catalog.send(a.owner(), "GET", "/api/v1/closures", null))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$").asArray().isEmpty();

        assertThat(catalog.send(b.owner(), "GET", "/api/v1/services", null))
                .bodyJson().extractingPath("$[*].name").asArray().containsExactly("Corte B");
        assertThat(catalog.send(b.owner(), "GET", "/api/v1/closures", null))
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(closureB.toString());
    }

    @Test
    void staffOfABusinessCanReadButNotChangeTheCatalog() {
        Session staffA = new AuthApi(mvc, jdbc, passwordEncoder).addStaff(a, "cat-staff");
        assertThat(catalog.send(staffA, "GET", "/api/v1/services", null)).hasStatus(HttpStatus.OK);
        assertThat(catalog.send(staffA, "GET", "/api/v1/staff/" + ownStaffA, null)).hasStatus(HttpStatus.OK);
        assertThat(catalog.send(staffA, "GET", "/api/v1/staff/" + ownStaffA + "/working-hours", null))
                .hasStatus(HttpStatus.OK);
        assertThat(catalog.send(staffA, "GET", "/api/v1/closures", null)).hasStatus(HttpStatus.OK);

        Stream.of(
                new Endpoint("POST", "/api/v1/services", SERVICE_BODY),
                new Endpoint("POST", "/api/v1/staff", STAFF_BODY),
                new Endpoint("PUT", "/api/v1/staff/" + ownStaffA, STAFF_BODY),
                new Endpoint("PUT", "/api/v1/staff/" + ownStaffA + "/services", "{\"serviceIds\": []}"),
                new Endpoint("PUT", "/api/v1/staff/" + ownStaffA + "/working-hours", "{\"slots\": []}"),
                new Endpoint("POST", "/api/v1/closures", """
                        {"startDate": "2026-12-24", "endDate": "2026-12-26"}"""))
                .forEach(e -> assertThat(catalog.send(staffA, e.method(), e.uri(), e.body()))
                        .as(e.toString())
                        .hasStatus(HttpStatus.FORBIDDEN));
    }

    private void assertBusinessBIsUntouched() {
        assertThat(catalog.send(b.owner(), "GET", "/api/v1/services/" + serviceB, null))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.name").isEqualTo("Corte B");
        assertThat(catalog.send(b.owner(), "GET", "/api/v1/staff/" + staffB, null))
                .hasStatus(HttpStatus.OK).bodyJson().isLenientlyEqualTo("""
                        {"displayName": "Berta", "userId": "%s"}""".formatted(b.ownerId()));
        assertThat(catalog.send(b.owner(), "GET", "/api/v1/staff/" + staffB + "/working-hours", null))
                .bodyJson().extractingPath("$.slots").asArray().isEmpty();
        assertThat(catalog.send(b.owner(), "GET", "/api/v1/closures?staffId=" + staffB, null))
                .bodyJson().extractingPath("$[*].id").asArray().containsExactly(closureB.toString());
        long intruders = jdbc.sql("SELECT count(*) FROM staff_member WHERE display_name IN ('Intruso', 'Robado')")
                .query(Long.class).single();
        assertThat(intruders).isZero();
    }
}
