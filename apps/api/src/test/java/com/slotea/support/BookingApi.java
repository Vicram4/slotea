package com.slotea.support;

import static com.slotea.support.SpaCsrf.spaCsrf;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;
import com.slotea.support.AuthApi.Session;

/** Atajos para la página pública de reservas: preparar un negocio reservable y reservar sin sesión. */
public final class BookingApi {

    /** Zona por defecto de los negocios registrados en los tests. */
    public static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private final MockMvcTester mvc;
    private final CatalogApi catalog;

    public BookingApi(MockMvcTester mvc) {
        this.mvc = mvc;
        this.catalog = new CatalogApi(mvc);
    }

    /**
     * Un día futuro dentro del horizonte de reservas. Los horarios de los tests cubren los 7 días de
     * la semana, así que vale cualquier día; los cambios de hora se prueban en AvailabilityCalculatorTest.
     */
    public static LocalDate aWeekFromNow() {
        return LocalDate.now(MADRID).plusDays(7);
    }

    /** Instante de una hora local de Madrid en ese día. */
    public static Instant at(LocalDate day, String time) {
        return day.atTime(LocalTime.parse(time)).atZone(MADRID).toInstant();
    }

    /** Profesional que hace ese servicio, de 09:00 a 14:00 todos los días. */
    public UUID createWorkingStaff(Session owner, String name, UUID... serviceIds) {
        UUID staffId = catalog.createStaff(owner, name, null);
        var ids = new StringBuilder();
        for (UUID id : serviceIds) {
            ids.append(ids.isEmpty() ? "" : ", ").append('"').append(id).append('"');
        }
        assertThat(catalog.send(owner, "PUT", "/api/v1/staff/" + staffId + "/services", """
                {"serviceIds": [%s]}""".formatted(ids))).hasStatus(HttpStatus.OK);
        var slots = new StringBuilder();
        for (int day = 1; day <= 7; day++) {
            slots.append(day == 1 ? "" : ", ").append("""
                    {"dayOfWeek": %d, "start": "09:00", "end": "14:00"}""".formatted(day));
        }
        assertThat(catalog.send(owner, "PUT", "/api/v1/staff/" + staffId + "/working-hours", """
                {"slots": [%s]}""".formatted(slots))).hasStatus(HttpStatus.OK);
        return staffId;
    }

    public MvcTestResult business(String slug) {
        return mvc.get().uri("/api/v1/public/businesses/{slug}", slug).exchange();
    }

    public MvcTestResult availability(String slug, UUID serviceId, @Nullable UUID staffId, LocalDate from,
            LocalDate to) {
        String staff = staffId == null ? "" : "&staffId=" + staffId;
        return mvc.get().uri("/api/v1/public/businesses/" + slug + "/availability?serviceId=" + serviceId + staff
                + "&from=" + from + "&to=" + to).exchange();
    }

    public MvcTestResult book(String slug, UUID serviceId, @Nullable UUID staffId, Instant startsAt, String email) {
        String staff = staffId == null ? "null" : "\"" + staffId + "\"";
        return bookJson(slug, """
                {"serviceId": "%s", "staffId": %s, "startsAt": "%s",
                 "customer": {"fullName": "Cliente de prueba", "email": "%s", "phone": "+34 600 000 000"}}
                """.formatted(serviceId, staff, startsAt, email));
    }

    /** Reserva y devuelve el token del enlace de gestión (el mismo que va en el email). */
    public String bookForToken(String slug, UUID serviceId, @Nullable UUID staffId, Instant startsAt, String email) {
        MvcTestResult result = book(slug, serviceId, staffId, startsAt, email);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(AuthApi.body(result), "$.manageToken");
    }

    // --- Enlace de gestión de la cita -----------------------------------------------------------

    private static String manage(String slug, String token) {
        return "/api/v1/public/businesses/" + slug + "/appointments/manage/" + token;
    }

    public MvcTestResult managed(String slug, String token) {
        return mvc.get().uri(manage(slug, token)).exchange();
    }

    public MvcTestResult cancel(String slug, String token) {
        return mvc.post().uri(manage(slug, token) + "/cancel").with(spaCsrf()).exchange();
    }

    public MvcTestResult manageAvailability(String slug, String token, LocalDate from, LocalDate to) {
        return mvc.get().uri(manage(slug, token) + "/availability?from=" + from + "&to=" + to).exchange();
    }

    public MvcTestResult reschedule(String slug, String token, Instant startsAt, @Nullable UUID staffId) {
        String staff = staffId == null ? "null" : "\"" + staffId + "\"";
        return mvc.post().uri(manage(slug, token) + "/reschedule").with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"startsAt": "%s", "staffId": %s}""".formatted(startsAt, staff))
                .exchange();
    }

    public MvcTestResult bookJson(String slug, String json) {
        return mvc.post().uri("/api/v1/public/businesses/{slug}/appointments", slug).with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}
