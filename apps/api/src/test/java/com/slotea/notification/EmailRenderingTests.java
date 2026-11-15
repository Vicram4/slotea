package com.slotea.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import com.slotea.TestcontainersConfiguration;
import com.slotea.notification.EmailRenderer.RenderedEmail;
import com.slotea.shared.links.AppointmentLinks;

/** Plantillas reales (Thymeleaf), sin enviar nada. */
@SpringBootTest
@AutoConfigureMockMvc // mismo contexto que el resto de tests de integración (se reutiliza)
@Import(TestcontainersConfiguration.class)
class EmailRenderingTests {

    static final UUID APPOINTMENT = UUID.fromString("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    @Autowired
    EmailRenderer renderer;

    @Autowired
    AppointmentLinks links;

    @Test
    void datesAreInSpanishAndInTheBusinessTimeZoneOnBothSidesOfTheDstChange() {
        // En 2026, Madrid pasa a horario de verano el domingo 29 de marzo: 08:00 UTC es 09:00 el sábado y 10:00 el domingo.
        assertThat(render(EmailType.BOOKING_CONFIRMED, "2026-03-28T08:00:00Z").subject())
                .isEqualTo("Cita confirmada: Corte, sábado 28 de marzo a las 09:00");
        assertThat(render(EmailType.BOOKING_CONFIRMED, "2026-03-29T08:00:00Z").subject())
                .isEqualTo("Cita confirmada: Corte, domingo 29 de marzo a las 10:00");
    }

    @Test
    void theConfirmationHasTheDetailsAndTheManageLinkInHtmlAndPlainText() {
        RenderedEmail email = render(EmailType.BOOKING_CONFIRMED, "2026-11-19T09:15:00Z");
        String manageUrl = "https://web.example.test/barberia/cita/" + links.token(APPOINTMENT);

        assertThat(email.text()).contains("Tu cita está confirmada · Barbería de prueba", "Hola, Laura:",
                "Corte", "Ana", "jueves 19 de noviembre de 2026", "10:15 – 10:45", manageUrl)
                .doesNotContain("<", "[(", "th:");
        assertThat(email.html()).contains("<html lang=\"es\"", "Tu cita está confirmada",
                "href=\"" + manageUrl + "\"", "jueves 19 de noviembre de 2026", "10:15 – 10:45")
                .doesNotContain(" th:", "Reservar otra cita"); // con espacio: «width:» también contiene «th:»
    }

    @Test
    void aRescheduledAppointmentShowsTheOldTimeToo() {
        var data = new AppointmentEmail(APPOINTMENT, "barberia", "Barbería de prueba", "negocio@example.test",
                "Europe/Madrid", "Laura", "Corte", "Ana", Instant.parse("2026-11-20T16:00:00Z"),
                Instant.parse("2026-11-20T16:30:00Z"), Instant.parse("2026-11-19T09:15:00Z"));

        RenderedEmail email = renderer.render(EmailType.BOOKING_RESCHEDULED, data);

        assertThat(email.subject()).isEqualTo("Cita cambiada: Corte, ahora el viernes 20 de noviembre a las 17:00");
        assertThat(email.text()).contains("Hemos cambiado tu cita", "17:00 – 17:30",
                "Antes:       jueves 19 de noviembre de 2026, 10:15");
        assertThat(email.html()).contains("Antes", "jueves 19 de noviembre de 2026, 10:15");
    }

    @Test
    void aCancellationOffersToBookAgainInsteadOfTheManageLink() {
        RenderedEmail email = render(EmailType.BOOKING_CANCELLED, "2026-11-19T09:15:00Z");

        assertThat(email.subject()).isEqualTo("Cita cancelada: Corte, jueves 19 de noviembre a las 10:15");
        assertThat(email.text()).contains("Tu cita está cancelada", "https://web.example.test/barberia")
                .doesNotContain("/cita/");
        assertThat(email.html()).contains("Reservar otra cita").doesNotContain("/cita/");
    }

    @Test
    void userInputIsEscapedInTheHtml() {
        var data = new AppointmentEmail(APPOINTMENT, "barberia", "Barbería <b>de</b> prueba",
                "negocio@example.test", "Europe/Madrid", "<script>alert(1)</script>", "Corte", "Ana",
                Instant.parse("2026-11-19T09:15:00Z"), Instant.parse("2026-11-19T09:45:00Z"), null);

        String html = renderer.render(EmailType.BOOKING_CONFIRMED, data).html();

        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "Barbería &lt;b&gt;de&lt;/b&gt; prueba")
                .doesNotContain("<script>", "<b>de</b>");
    }

    private RenderedEmail render(EmailType type, String startsAt) {
        Instant start = Instant.parse(startsAt);
        return renderer.render(type, new AppointmentEmail(APPOINTMENT, "barberia", "Barbería de prueba",
                "negocio@example.test", "Europe/Madrid", "Laura", "Corte", "Ana", start,
                start.plusSeconds(30 * 60), null));
    }
}
