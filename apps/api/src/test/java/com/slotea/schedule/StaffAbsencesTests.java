package com.slotea.schedule;

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

import com.jayway.jsonpath.JsonPath;
import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Session;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.CatalogApi;

/** Un empleado gestiona sus propias ausencias, y nada más: ni las de otros ni los cierres del negocio. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffAbsencesTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    CatalogApi catalog;
    AuthApi auth;
    Tenant tenant;
    UUID ana;
    UUID bruno;
    Session anaUser;

    @BeforeEach
    void employeeLinkedToAna() {
        auth = new AuthApi(mvc, jdbc, passwordEncoder);
        catalog = new CatalogApi(mvc);
        tenant = auth.signup("absences");
        var user = auth.addStaffUser(tenant, "ana");
        ana = catalog.createStaff(tenant.owner(), "Ana", user.userId());
        bruno = catalog.createStaff(tenant.owner(), "Bruno", null);
        anaUser = user.session();
    }

    @Test
    void anEmployeeCreatesAndDeletesTheirOwnAbsences() {
        var created = catalog.send(anaUser, "POST", "/api/v1/closures", absence(ana));
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = JsonPath.read(AuthApi.body(created), "$.id");

        assertThat(catalog.send(anaUser, "DELETE", "/api/v1/closures/" + id, null)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void anEmployeeCannotTouchOtherAbsencesOrBusinessClosures() {
        UUID brunoAbsence = catalog.createClosure(tenant.owner(), bruno, "2026-12-24", "2026-12-26");
        UUID businessClosure = catalog.createClosure(tenant.owner(), null, "2026-12-31", "2027-01-01");

        assertThat(catalog.send(anaUser, "POST", "/api/v1/closures", absence(bruno))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(catalog.send(anaUser, "POST", "/api/v1/closures", """
                {"startDate": "2026-12-24", "endDate": "2026-12-26"}""")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(catalog.send(anaUser, "DELETE", "/api/v1/closures/" + brunoAbsence, null))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(catalog.send(anaUser, "DELETE", "/api/v1/closures/" + businessClosure, null))
                .hasStatus(HttpStatus.FORBIDDEN);

        assertThat(jdbc.sql("SELECT count(*) FROM closure WHERE business_id = ?").param(tenant.businessId())
                .query(Long.class).single()).isEqualTo(2);
    }

    @Test
    void anEmployeeWithoutAProfessionalProfileCannotCreateAbsences() {
        Session unlinked = auth.addStaff(tenant, "sin-ficha");
        assertThat(catalog.send(unlinked, "POST", "/api/v1/closures", absence(ana))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void theOwnerStillManagesEverything() {
        UUID anaAbsence = catalog.createClosure(anaUser, ana, "2026-12-24", "2026-12-26");
        assertThat(catalog.send(tenant.owner(), "DELETE", "/api/v1/closures/" + anaAbsence, null))
                .hasStatus(HttpStatus.NO_CONTENT);
    }

    private static String absence(UUID staffId) {
        return """
                {"staffId": "%s", "startDate": "2026-12-24", "endDate": "2026-12-26", "reason": "Médico"}"""
                .formatted(staffId);
    }
}
