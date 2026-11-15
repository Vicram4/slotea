package com.slotea.notification;

import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.jayway.jsonpath.JsonPath;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;

/** Datos de prueba de los emails. Los emails apuntan a una cita real (la outbox tiene FK a la cita). */
final class EmailFixtures {

    private EmailFixtures() {
    }

    /** Reserva una cita en ese negocio (manda su propia confirmación) y devuelve su id. */
    static UUID bookedAppointment(MockMvcTester mvc, Tenant tenant) {
        var catalog = new CatalogApi(mvc);
        var booking = new BookingApi(mvc);
        UUID haircut = catalog.createService(tenant.owner(), "Corte");
        UUID ana = booking.createWorkingStaff(tenant.owner(), "Ana", haircut);
        var result = booking.book(tenant.slug(), haircut, ana, at(aWeekFromNow(), "09:00"),
                AuthApi.unique("booker") + "@example.test");
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(AuthApi.body(result), "$.id"));
    }

    static AppointmentEmail email(UUID appointmentId, String slug, Instant startsAt) {
        return new AppointmentEmail(appointmentId, slug, "Barbería de prueba", "negocio@example.test",
                "Europe/Madrid", "Laura", "Corte", "Ana", startsAt, startsAt.plus(Duration.ofMinutes(30)), null,
                "laura@example.test", null);
    }

    static String uniqueAddress() {
        return AuthApi.unique("cliente") + "@example.test";
    }
}
