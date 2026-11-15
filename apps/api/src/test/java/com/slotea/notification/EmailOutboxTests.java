package com.slotea.notification;

import static com.slotea.notification.EmailFixtures.email;
import static com.slotea.notification.EmailFixtures.uniqueAddress;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.slotea.TestcontainersConfiguration;
import com.slotea.notification.EmailDispatcher.DeliveryReport;
import com.slotea.shared.links.AppointmentLinks;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.Mailpit;

/** La outbox con PostgreSQL y un SMTP reales (Mailpit). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmailOutboxTests {

    static final String JOBS_TOKEN = "test-only-jobs-token-not-a-real-secret";

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

    @Autowired
    AppointmentLinks links;

    @Autowired
    Mailpit mailpit;

    Tenant tenant;
    UUID appointmentId;

    @BeforeEach
    void appointment() {
        tenant = new AuthApi(mvc, jdbc, passwordEncoder).signup("outbox");
        appointmentId = EmailFixtures.bookedAppointment(mvc, tenant);
    }

    @Test
    void emailsAreQueuedOnlyInsideTheTransactionThatChangesTheData() {
        assertThatThrownBy(() -> TenantContext.callAs(tenant.businessId(),
                () -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, uniqueAddress(), sample())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aRolledBackChangeSendsNothing() {
        String to = uniqueAddress();

        assertThatThrownBy(() -> inTenantTransaction(() -> {
            outbox.enqueue(EmailType.BOOKING_CONFIRMED, to, sample());
            throw new IllegalStateException("la reserva falla después de encolar el email");
        })).hasMessageContaining("la reserva falla");

        dispatcher.deliverDue(OutboxJobController.MAX_PER_RUN);
        assertThat(rowsTo(to)).isEmpty();
        assertThat(mailpit.to(to)).isEmpty();
    }

    @Test
    void sendNowDeliversAQueuedEmailAtOnce() {
        String to = uniqueAddress();
        UUID id = inTenantTransaction(() -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, to, sample()));

        outbox.sendNow(id);

        assertThat(mailpit.awaitOne(to).subject()).startsWith("Cita confirmada: Corte");
        assertThat(rowsTo(to)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("status", "SENT").containsEntry("attempts", 1);
            assertThat(row.get("sent_at")).isNotNull();
        });
    }

    @Test
    void sendNowRefusesToRunInsideTheTransaction() {
        assertThatThrownBy(() -> inTenantTransaction(() -> {
            outbox.sendNow(outbox.enqueue(EmailType.BOOKING_CONFIRMED, uniqueAddress(), sample()));
            return null;
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("after the transaction commits");
    }

    @Test
    void theJobSendsWhatIsPending() {
        String to = uniqueAddress();
        inTenantTransaction(() -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, to, sample()));
        assertThat(mailpit.to(to)).as("encolado pero sin enviar todavía").isEmpty();

        assertThat(mvc.post().uri("/internal/jobs/outbox").header("X-Jobs-Token", JOBS_TOKEN))
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.sent").asNumber().isNotEqualTo(0);

        mailpit.awaitOne(to);
        assertThat(rowsTo(to)).singleElement().satisfies(row -> assertThat(row).containsEntry("status", "SENT"));
    }

    @Test
    void demoBusinessesNeverSendEmails() {
        jdbc.sql("UPDATE business SET is_demo = true WHERE id = ?").param(tenant.businessId()).update();
        String to = uniqueAddress();
        UUID id = inTenantTransaction(() -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, to, sample()));

        outbox.sendNow(id);
        assertThat(mvc.post().uri("/internal/jobs/outbox").header("X-Jobs-Token", JOBS_TOKEN)).hasStatus(HttpStatus.OK);

        assertThat(rowsTo(to)).singleElement().satisfies(row ->
                assertThat(row).containsEntry("status", "DEMO").containsEntry("attempts", 0));
        assertThat(mailpit.to(to)).isEmpty();
    }

    @Test
    void twoJobRunsAtTheSameTimeSendEachEmailOnce() throws Exception {
        List<String> addresses = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            addresses.add(uniqueAddress());
        }
        inTenantTransaction(() -> {
            addresses.forEach(to -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, to, sample()));
            return null;
        });

        var start = new CountDownLatch(1);
        List<Future<DeliveryReport>> runs = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return dispatcher.deliverDue(OutboxJobController.MAX_PER_RUN);
                }));
            }
            start.countDown();
        }

        int sent = runs.get(0).get().sent() + runs.get(1).get().sent();
        assertThat(sent).isGreaterThanOrEqualTo(addresses.size());
        for (String to : addresses) {
            // Un solo intento por email: SKIP LOCKED reparte los emails entre las dos ejecuciones.
            assertThat(rowsTo(to)).singleElement()
                    .satisfies(row -> assertThat(row).containsEntry("status", "SENT").containsEntry("attempts", 1));
            assertThat(mailpit.await(to, 1)).hasSize(1);
        }
    }

    @Test
    void theStoredPayloadNeverContainsTheLinkToken() {
        String to = uniqueAddress();
        inTenantTransaction(() -> outbox.enqueue(EmailType.BOOKING_CONFIRMED, to, sample()));

        String payload = jdbc.sql("SELECT CAST(payload AS text) FROM email_outbox WHERE to_email = ?").param(to)
                .query(String.class).single();
        assertThat(payload).contains(appointmentId.toString()).doesNotContain(links.token(appointmentId));
    }

    private AppointmentEmail sample() {
        return email(appointmentId, tenant.slug(), Instant.parse("2030-01-15T09:00:00Z"));
    }

    private <T> T inTenantTransaction(java.util.function.Supplier<T> work) {
        return TenantContext.callAs(tenant.businessId(),
                () -> new TransactionTemplate(transactionManager).execute(_ -> work.get()));
    }

    private List<Map<String, Object>> rowsTo(String to) {
        return jdbc.sql("SELECT status, attempts, sent_at FROM email_outbox WHERE to_email = ?").param(to)
                .query().listOfRows();
    }
}
