package com.slotea.business;

import static org.assertj.core.api.Assertions.assertThat;
import static com.slotea.support.SpaCsrf.spaCsrf;

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
import com.slotea.support.AuthApi.Session;
import com.slotea.support.AuthApi.Tenant;

/**
 * Regla del proyecto: el negocio A no ve nada del negocio B (404, nunca 403) y cada rol solo puede
 * hacer lo suyo. Cada endpoint nuevo con datos de negocio añade aquí (o en su módulo) su caso.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantIsolationTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    Tenant a;
    Tenant b;
    Session staffA;

    @BeforeEach
    void twoTenants() {
        var api = new AuthApi(mvc, jdbc, passwordEncoder);
        a = api.signup("a");
        b = api.signup("b");
        staffA = api.addStaff(a, "staff-a");
    }

    @Test
    void eachOwnerSeesOnlyTheirOwnBusiness() {
        assertThat(mvc.get().uri("/api/v1/business").cookie(a.owner().access()))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.id").isEqualTo(a.businessId().toString());
        assertThat(mvc.get().uri("/api/v1/business").cookie(b.owner().access()))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.id").isEqualTo(b.businessId().toString());
    }

    @Test
    void membersListContainsOnlyTheCurrentBusiness() {
        assertThat(mvc.get().uri("/api/v1/business/members").cookie(a.owner().access()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[*].role")
                .asArray()
                .containsExactly("OWNER", "STAFF");
        assertThat(mvc.get().uri("/api/v1/business/members").cookie(b.owner().access()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$[*].userId")
                .asArray()
                .containsExactly(b.ownerId().toString());
    }

    @Test
    void memberOfAnotherBusinessIsNotFound() {
        assertThat(mvc.get().uri("/api/v1/business/members/{id}", a.ownerId()).cookie(a.owner().access()))
                .hasStatus(HttpStatus.OK);
        assertThat(mvc.get().uri("/api/v1/business/members/{id}", b.ownerId()).cookie(a.owner().access()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void updatingTheBusinessOnlyChangesTheCurrentOne() {
        assertThat(mvc.patch().uri("/api/v1/business").with(spaCsrf()).cookie(a.owner().access())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Nuevo nombre A", "timezone": "Atlantic/Canary"}
                        """))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name": "Nuevo nombre A", "timezone": "Atlantic/Canary", "slug": "%s"}
                        """.formatted(a.slug()));

        assertThat(mvc.get().uri("/api/v1/business").cookie(b.owner().access()))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name": "Negocio b", "timezone": "Europe/Madrid"}
                        """);
    }

    @Test
    void invalidUpdateIsRejected() {
        assertThat(mvc.patch().uri("/api/v1/business").with(spaCsrf()).cookie(a.owner().access())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "  ", "timezone": "+02:00"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("name", "timezone");
    }

    @Test
    void staffCanReadTheBusinessButNotManageIt() {
        assertThat(mvc.get().uri("/api/v1/business").cookie(staffA.access()))
                .hasStatus(HttpStatus.OK);
        assertThat(mvc.patch().uri("/api/v1/business").with(spaCsrf()).cookie(staffA.access())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Hack\"}"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(mvc.get().uri("/api/v1/business/members").cookie(staffA.access()))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/business/members/{id}", a.ownerId()).cookie(staffA.access()))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void staffMeShowsTheStaffRole() {
        assertThat(mvc.get().uri("/api/v1/me").cookie(staffA.access()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.activeBusiness.role").isEqualTo("STAFF");
    }
}
