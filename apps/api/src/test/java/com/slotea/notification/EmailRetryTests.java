package com.slotea.notification;

import static com.slotea.notification.EmailFixtures.email;
import static com.slotea.notification.EmailFixtures.uniqueAddress;
import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;

/** Qué pasa cuando el SMTP falla: la reserva no se entera, y el email se reintenta con espera creciente. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailRetryTests {

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    EmailOutbox outbox;

    @Autowired
    EmailDispatcher dispatcher;

    Tenant tenant;

    @BeforeEach
    void smtpIsDown() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessagePreparator.class));
        tenant = new AuthApi(mvc, jdbc, passwordEncoder).signup("retry");
    }

    @Test
    void theBookingSucceedsAndItsEmailWaitsForTheJob() {
        var catalog = new CatalogApi(mvc);
        var booking = new BookingApi(mvc);
        UUID haircut = catalog.createService(tenant.owner(), "Corte");
        UUID ana = booking.createWorkingStaff(tenant.owner(), "Ana", haircut);
        String to = uniqueAddress();

        assertThat(booking.book(tenant.slug(), haircut, ana, at(aWeekFromNow(), "09:00"), to))
                .hasStatus(HttpStatus.CREATED);

        Map<String, Object> row = row(to);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        assertThat((String) row.get("last_error")).contains("SMTP down");
        assertThat(nextAttempt(row)).isCloseTo(Instant.now().plus(Duration.ofMinutes(1)), within(15, ChronoUnit.SECONDS));
    }

    @Test
    void failuresBackOffAndEventuallyGiveUp() {
        String to = uniqueAddress();
        enqueue(to);

        for (int attempt = 1; attempt <= EmailDispatcher.MAX_ATTEMPTS; attempt++) {
            makeDue(to);
            dispatcher.deliverDue(OutboxJobController.MAX_PER_RUN);

            Map<String, Object> row = row(to);
            assertThat(row).containsEntry("attempts", attempt);
            if (attempt < EmailDispatcher.MAX_ATTEMPTS) {
                assertThat(row).containsEntry("status", "PENDING");
                Duration wait = EmailDispatcher.BACKOFF.get(attempt - 1);
                assertThat(nextAttempt(row)).as("espera tras el intento %d", attempt)
                        .isCloseTo(Instant.now().plus(wait), within(15, ChronoUnit.SECONDS));
            } else {
                assertThat(row).containsEntry("status", "FAILED");
            }
        }

        makeDue(to);
        dispatcher.deliverDue(OutboxJobController.MAX_PER_RUN);
        assertThat(row(to)).as("un email perdido no se vuelve a intentar").containsEntry("attempts", 5);
    }

    @Test
    void anEmailIsNotRetriedBeforeItsTurn() {
        String to = uniqueAddress();
        outbox.sendNow(enqueue(to)); // falla: próximo intento dentro de 1 min

        dispatcher.deliverDue(OutboxJobController.MAX_PER_RUN);

        assertThat(row(to)).containsEntry("attempts", 1);
    }

    @Test
    void whenTheSmtpComesBackTheEmailIsSent() {
        String to = uniqueAddress();
        outbox.sendNow(enqueue(to));
        assertThat(row(to)).containsEntry("status", "PENDING");

        doNothing().when(mailSender).send(any(MimeMessagePreparator.class));
        makeDue(to);
        dispatcher.deliverDue(OutboxJobController.MAX_PER_RUN);

        Map<String, Object> row = row(to);
        assertThat(row).containsEntry("status", "SENT").containsEntry("attempts", 2);
        assertThat(row.get("last_error")).isNull();
    }

    /** El latido semanal sí tiene que fallar: así el workflow se pone en rojo y GitHub avisa. */
    @Test
    void theMailHeartbeatFailsLoudlyWhenTheSmtpIsDown() {
        assertThat(mvc.post().uri("/internal/jobs/mail-heartbeat")
                .header("X-Jobs-Token", "test-only-jobs-token-not-a-real-secret"))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE).bodyJson()
                .extractingPath("$.detail").isEqualTo("El servidor de correo no ha aceptado el email de comprobación.");
    }

    private UUID enqueue(String to) {
        UUID appointmentId = EmailFixtures.bookedAppointment(mvc, tenant);
        return TenantContext.callAs(tenant.businessId(), () -> new TransactionTemplate(transactionManager)
                .execute(_ -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, to,
                        email(appointmentId, tenant.slug(), Instant.parse("2030-01-15T09:00:00Z")))));
    }

    /** Simula que ha pasado el tiempo de espera. */
    private void makeDue(String to) {
        jdbc.sql("UPDATE email_outbox SET next_attempt_at = now() - interval '1 second' WHERE to_email = ?")
                .param(to).update();
    }

    private Map<String, Object> row(String to) {
        return jdbc.sql("SELECT status, attempts, next_attempt_at, last_error FROM email_outbox WHERE to_email = ?")
                .param(to).query().singleRow();
    }

    private static Instant nextAttempt(Map<String, Object> row) {
        return ((Timestamp) row.get("next_attempt_at")).toInstant();
    }
}
