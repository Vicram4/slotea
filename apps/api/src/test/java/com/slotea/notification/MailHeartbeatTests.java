package com.slotea.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.slotea.TestcontainersConfiguration;
import com.slotea.support.Mailpit;

/**
 * El latido semanal del email llega de verdad al remitente de la plataforma (Mailpit). El caso del SMTP
 * caído está en {@link EmailRetryTests}; el token, en {@code InternalJobsSecurityTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MailHeartbeatTests {

    static final String JOBS_TOKEN = "test-only-jobs-token-not-a-real-secret";
    /** La dirección de {@code MAIL_FROM} en los tests (valor por defecto de application.yaml). */
    static final String PLATFORM = "reservas@slotea.test";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    Mailpit mailpit;

    @Test
    void sendsARealEmailToThePlatformSender() {
        int before = mailpit.to(PLATFORM).size();

        assertThat(mvc.post().uri("/internal/jobs/mail-heartbeat").header("X-Jobs-Token", JOBS_TOKEN))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.status").isEqualTo("sent");

        var email = mailpit.await(PLATFORM, before + 1).getFirst();
        assertThat(email.subject()).isEqualTo("Slotea · comprobación semanal del email");
        assertThat(email.from()).isEqualTo(PLATFORM);
        assertThat(email.to()).containsExactly(PLATFORM);
    }

    @Test
    void needsTheJobsToken() {
        int before = mailpit.to(PLATFORM).size();

        assertThat(mvc.post().uri("/internal/jobs/mail-heartbeat")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mailpit.to(PLATFORM)).hasSize(before);
    }
}
