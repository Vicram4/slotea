package com.slotea.booking;

import static com.slotea.support.BookingApi.aWeekFromNow;
import static com.slotea.support.BookingApi.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

import com.slotea.TestcontainersConfiguration;
import com.slotea.support.AuthApi;
import com.slotea.support.AuthApi.Tenant;
import com.slotea.support.BookingApi;
import com.slotea.support.CatalogApi;

/**
 * La página pública de A no sirve para ver ni reservar nada de B: el tenant sale del slug de la ruta,
 * y los ids de B en la petición dan 404 (como si no existieran).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingIsolationTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    BookingApi booking;
    Tenant a;
    Tenant b;
    UUID serviceA;
    UUID staffA;
    UUID serviceB;
    UUID staffB;
    LocalDate day;

    @BeforeEach
    void twoBookableBusinesses() {
        var auth = new AuthApi(mvc, jdbc, passwordEncoder);
        var catalog = new CatalogApi(mvc);
        booking = new BookingApi(mvc);
        a = auth.signup("book-a");
        b = auth.signup("book-b");
        serviceA = catalog.createService(a.owner(), "Corte A");
        staffA = booking.createWorkingStaff(a.owner(), "Ana", serviceA);
        serviceB = catalog.createService(b.owner(), "Corte B");
        staffB = booking.createWorkingStaff(b.owner(), "Berta", serviceB);
        day = aWeekFromNow();
    }

    @Test
    void idsOfAnotherBusinessAreNotFoundThroughThisBusinessSlug() {
        assertThat(booking.availability(a.slug(), serviceB, null, day, day)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.availability(a.slug(), serviceA, staffB, day, day)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.book(a.slug(), serviceB, null, at(day, "09:00"), "x@example.test"))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.book(a.slug(), serviceA, staffB, at(day, "09:00"), "x@example.test"))
                .hasStatus(HttpStatus.NOT_FOUND);

        assertThat(count("appointment", b)).isZero();
        assertThat(count("customer", b)).isZero();
        assertThat(count("appointment", a)).isZero();
    }

    @Test
    void eachPublicPageOnlyShowsItsOwnBusiness() {
        assertThat(booking.business(a.slug())).bodyJson().extractingPath("$.services[*].name").asArray()
                .containsExactly("Corte A");
        assertThat(booking.business(b.slug())).bodyJson().extractingPath("$.staff[*].displayName").asArray()
                .containsExactly("Berta");
    }

    @Test
    void aPanelSessionOfAnotherBusinessDoesNotChangeTheTenant() {
        // El dueño de B navega por la página de A con su cookie: manda el slug, no el JWT.
        assertThat(mvc.get().uri("/api/v1/public/businesses/{slug}", a.slug()).cookie(b.owner().access()).exchange())
                .hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.services[*].name").asArray()
                .containsExactly("Corte A");
    }

    @Test
    void theSameCustomerEmailIsADifferentCustomerInEachBusiness() {
        assertThat(booking.book(a.slug(), serviceA, staffA, at(day, "09:00"), "misma@example.test"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(booking.book(b.slug(), serviceB, staffB, at(day, "09:00"), "misma@example.test"))
                .hasStatus(HttpStatus.CREATED);

        assertThat(count("customer", a)).isEqualTo(1);
        assertThat(count("customer", b)).isEqualTo(1);
    }

    @Test
    void aManageLinkOnlyWorksUnderItsOwnBusiness() {
        String tokenB = booking.bookForToken(b.slug(), serviceB, staffB, at(day, "09:00"),
                AuthApi.unique("b") + "@example.test");

        // El token es válido, pero no en el negocio A: 404, como uno inventado. Y B queda intacto.
        assertThat(booking.managed(a.slug(), tokenB)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.manageAvailability(a.slug(), tokenB, day, day)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.cancel(a.slug(), tokenB)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(booking.reschedule(a.slug(), tokenB, at(day, "10:00"), null)).hasStatus(HttpStatus.NOT_FOUND);

        assertThat(booking.managed(b.slug(), tokenB)).hasStatus(HttpStatus.OK)
                .bodyJson().isLenientlyEqualTo("""
                        {"status": "CONFIRMED", "time": "09:00"}""");
    }

    private long count(String table, Tenant tenant) {
        return jdbc.sql("SELECT count(*) FROM " + table + " WHERE business_id = ?").param(tenant.businessId())
                .query(Long.class).single();
    }
}
