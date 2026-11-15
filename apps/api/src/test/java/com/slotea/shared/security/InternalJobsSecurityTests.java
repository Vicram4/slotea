package com.slotea.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

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

/** {@code /internal/jobs/*}: solo con el token del cron. Ni sin él, ni con uno falso, ni con la sesión del panel. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InternalJobsSecurityTests {

    static final String JOBS_TOKEN = "test-only-jobs-token-not-a-real-secret";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void withoutTheTokenTheJobIsUnauthorized() {
        assertThat(mvc.post().uri("/internal/jobs/outbox"))
                .hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aWrongTokenIsUnauthorized() {
        assertThat(mvc.post().uri("/internal/jobs/outbox").header(InternalJobsSecurity.HEADER, JOBS_TOKEN + "x"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/internal/jobs/outbox").header("Authorization", "Bearer " + JOBS_TOKEN))
                .as("el token va en su cabecera, no como bearer")
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aPanelSessionIsNotEnough() {
        var owner = new AuthApi(mvc, jdbc, passwordEncoder).signup("jobs").owner();

        assertThat(mvc.post().uri("/internal/jobs/outbox").cookie(owner.access(), owner.refresh()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theRightTokenWorksWithoutCsrf() {
        assertThat(mvc.post().uri("/internal/jobs/outbox").header(InternalJobsSecurity.HEADER, JOBS_TOKEN))
                .hasStatus(HttpStatus.OK).bodyJson()
                .hasPath("$.sent").hasPath("$.retrying").hasPath("$.failed");
    }
}
