package com.slotea.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static com.slotea.support.SpaCsrf.spaCsrf;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.slotea.TestcontainersConfiguration;
import com.slotea.shared.security.AuthClaims;
import com.slotea.shared.security.OpaqueTokens;
import com.slotea.shared.web.ApiException;
import com.slotea.support.AuthApi;

import jakarta.servlet.http.Cookie;

/** Registro, login, «quién soy», refresh rotatorio y logout contra PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JwtEncoder jwtEncoder;

    @Autowired
    AuthService authService;

    AuthApi api;

    @BeforeEach
    void setUp() {
        api = new AuthApi(mvc, jdbc, passwordEncoder);
    }

    // --- Registro --------------------------------------------------------------------------------

    @Test
    void signupCreatesBusinessOwnerAndSessionCookies() {
        String slug = AuthApi.unique("barberia");
        MvcTestResult result = mvc.post().uri("/api/v1/auth/signup").with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(AuthApi.signupJson("Barbería Demo", slug, "Ana Demo", slug + "@Example.TEST"))
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson()
                .hasPathSatisfying("$.user.email", v -> v.assertThat().isEqualTo(slug + "@example.test"))
                .hasPathSatisfying("$.activeBusiness.slug", v -> v.assertThat().isEqualTo(slug))
                .hasPathSatisfying("$.activeBusiness.role", v -> v.assertThat().isEqualTo("OWNER"))
                .doesNotHavePath("$.accessToken");

        Cookie access = result.getResponse().getCookie(AuthClaims.ACCESS_COOKIE);
        assertThat(access).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSecure()).isTrue();
        assertThat(access.getPath()).isEqualTo("/");
        assertThat(access.getMaxAge()).isEqualTo(15 * 60);
        assertThat(access.getAttribute("SameSite")).isEqualTo("Lax");

        Cookie refresh = result.getResponse().getCookie(AuthClaims.REFRESH_COOKIE);
        assertThat(refresh).isNotNull();
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        assertThat(refresh.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
        assertThat(refresh.getAttribute("SameSite")).isEqualTo("Lax");

        // En la BD solo está el hash del refresh token y la contraseña con BCrypt.
        byte[] stored = jdbc.sql("""
                SELECT t.token_hash FROM refresh_token t JOIN app_user u ON u.id = t.user_id WHERE u.email = ?
                """).param(slug + "@example.test").query(byte[].class).single();
        assertThat(stored).isEqualTo(OpaqueTokens.sha256(refresh.getValue()));
        String passwordHash = jdbc.sql("SELECT password_hash FROM app_user WHERE email = ?")
                .param(slug + "@example.test").query(String.class).single();
        assertThat(passwordHash).startsWith("{bcrypt}").doesNotContain(AuthApi.PASSWORD);
    }

    @Test
    void signupRejectsEmailThatExistsWithDifferentCase() {
        var tenant = api.signup("dup");
        String json = AuthApi.signupJson("Otro", AuthApi.unique("otro"), "Otra", tenant.ownerEmail().toUpperCase());

        assertThat(mvc.post().uri("/api/v1/auth/signup").with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void signupRejectsTakenAndReservedSlugs() {
        var tenant = api.signup("taken");
        for (String slug : List.of(tenant.slug(), "panel", "api")) {
            String email = AuthApi.unique("slug") + "@example.test";
            assertThat(mvc.post().uri("/api/v1/auth/signup").with(spaCsrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(AuthApi.signupJson("Otro", slug, "Otra", email)))
                    .as(slug)
                    .hasStatus(HttpStatus.CONFLICT);
        }
    }

    @Test
    void signupValidatesFieldsAndListsErrors() {
        assertThat(mvc.post().uri("/api/v1/auth/signup").with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"businessName": "X", "slug": "-Mal-", "timezone": "Mars/Base", "fullName": "Y",
                         "email": "no-es-un-email", "password": "corta"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("slug", "timezone", "email", "password");
    }

    @Test
    void signupWithoutCsrfTokenIsForbidden() {
        assertThat(mvc.post().uri("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(AuthApi.signupJson("X", AuthApi.unique("csrf"), "Y", AuthApi.unique("c") + "@example.test")))
                .hasStatus(HttpStatus.FORBIDDEN)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    /** El flujo real de la SPA: pedir la cookie XSRF-TOKEN y devolverla en la cabecera. */
    @Test
    void csrfCookieAndHeaderAllowPosting() {
        MvcTestResult csrfResult = mvc.get().uri("/api/v1/auth/csrf").exchange();
        assertThat(csrfResult).hasStatus(HttpStatus.NO_CONTENT);
        Cookie xsrf = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).as("la SPA tiene que poder leerla").isFalse();

        String slug = AuthApi.unique("spa");
        assertThat(mvc.post().uri("/api/v1/auth/signup")
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(AuthApi.signupJson("SPA", slug, "Spa", slug + "@example.test")))
                .hasStatus(HttpStatus.CREATED);
    }

    // --- Login y «quién soy» ---------------------------------------------------------------------

    @Test
    void loginFailsWithTheSameMessageForWrongPasswordAndUnknownEmail() {
        var tenant = api.signup("login");
        MvcTestResult wrongPassword = api.loginResult(tenant.ownerEmail(), "wrong-password-123");
        MvcTestResult unknownEmail = api.loginResult(AuthApi.unique("nadie") + "@example.test", AuthApi.PASSWORD);

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getCookie(AuthClaims.ACCESS_COOKIE)).isNull();
        assertThat(AuthApi.body(wrongPassword)).as("no revela si el email existe")
                .isEqualTo(AuthApi.body(unknownEmail));
    }

    @Test
    void loginIsCaseInsensitiveOnEmailAndReturnsMe() {
        var tenant = api.signup("case");
        assertThat(api.loginResult(tenant.ownerEmail().toUpperCase(), AuthApi.PASSWORD))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.activeBusiness.businessId").isEqualTo(tenant.businessId().toString());
    }

    @Test
    void meRequiresSessionAndReturnsUserBusinessAndRole() {
        var tenant = api.signup("me");
        assertThat(mvc.get().uri("/api/v1/me")).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.get().uri("/api/v1/me").cookie(tenant.owner().access()))
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"user": {"email": "%s"},
                         "activeBusiness": {"businessId": "%s", "slug": "%s", "role": "OWNER"},
                         "memberships": [{"businessId": "%s"}]}
                        """.formatted(tenant.ownerEmail(), tenant.businessId(), tenant.slug(), tenant.businessId()));
    }

    @Test
    void tamperedOrExpiredAccessTokenIsRejected() {
        var tenant = api.signup("jwt");
        String token = tenant.owner().access().getValue();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(AuthClaims.ACCESS_COOKIE, tampered)))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        Instant past = Instant.now().minusSeconds(3600);
        var claims = JwtClaimsSet.builder().issuer(AuthClaims.ISSUER).subject(tenant.ownerId().toString())
                .issuedAt(past).expiresAt(past.plusSeconds(900)).build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
        assertThat(mvc.get().uri("/api/v1/me").cookie(new Cookie(AuthClaims.ACCESS_COOKIE, expired)))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /** Una cookie de acceso caducada no puede impedir volver a entrar. */
    @Test
    void staleAccessCookieDoesNotBlockPublicAuthEndpoints() {
        var tenant = api.signup("stale");
        assertThat(mvc.post().uri("/api/v1/auth/login").with(spaCsrf())
                .cookie(new Cookie(AuthClaims.ACCESS_COOKIE, "garbage"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(tenant.ownerEmail(), AuthApi.PASSWORD)))
                .hasStatus(HttpStatus.OK);
    }

    // --- Refresh y logout ------------------------------------------------------------------------

    @Test
    void refreshRotatesTheTokenAndKeepsTheSessionAlive() {
        var tenant = api.signup("rotate");
        MvcTestResult result = api.refresh(tenant.owner().refresh());
        assertThat(result).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.activeBusiness.role").isEqualTo("OWNER");

        var next = AuthApi.sessionOf(result);
        assertThat(next.refresh().getValue()).isNotEqualTo(tenant.owner().refresh().getValue());
        assertThat(mvc.get().uri("/api/v1/me").cookie(next.access())).hasStatus(HttpStatus.OK);

        // El anterior queda revocado y enlazado con su sucesor.
        var old = jdbc.sql("SELECT revoked_at IS NOT NULL AS revoked, replaced_by FROM refresh_token WHERE token_hash = ?")
                .param(OpaqueTokens.sha256(tenant.owner().refresh().getValue()))
                .query((rs, _) -> new Object[] { rs.getBoolean("revoked"), rs.getObject("replaced_by") })
                .single();
        assertThat(old[0]).isEqualTo(true);
        assertThat(old[1]).isNotNull();
    }

    /** Reutilizar un refresh ya rotado es señal de robo: se cierra la sesión entera (toda la familia). */
    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() {
        var tenant = api.signup("reuse");
        var next = AuthApi.sessionOf(api.refresh(tenant.owner().refresh()));

        MvcTestResult reuse = api.refresh(tenant.owner().refresh());
        assertThat(reuse).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(reuse.getResponse().getCookie(AuthClaims.REFRESH_COOKIE).getMaxAge()).isZero();

        assertThat(api.refresh(next.refresh())).as("el token legítimo también cae").hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshWithoutOrWithUnknownCookieIsUnauthorized() {
        assertThat(mvc.post().uri("/api/v1/auth/refresh").with(spaCsrf())).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(api.refresh(new Cookie(AuthClaims.REFRESH_COOKIE, OpaqueTokens.generate())))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void expiredRefreshTokenIsUnauthorized() {
        var tenant = api.signup("expired");
        jdbc.sql("UPDATE refresh_token SET expires_at = now() - interval '1 second' WHERE token_hash = ?")
                .param(OpaqueTokens.sha256(tenant.owner().refresh().getValue()))
                .update();
        assertThat(api.refresh(tenant.owner().refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsCookies() {
        var tenant = api.signup("logout");
        MvcTestResult result = mvc.post().uri("/api/v1/auth/logout").with(spaCsrf())
                .cookie(tenant.owner().refresh()).exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(result.getResponse().getCookie(AuthClaims.ACCESS_COOKIE).getMaxAge()).isZero();
        assertThat(result.getResponse().getCookie(AuthClaims.REFRESH_COOKIE).getMaxAge()).isZero();
        assertThat(api.refresh(tenant.owner().refresh())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    /** Dos pestañas refrescan a la vez con el mismo token: solo una puede ganar. */
    @Test
    void concurrentRefreshWithTheSameTokenSucceedsAtMostOnce() throws Exception {
        var tenant = api.signup("race");
        String token = tenant.owner().refresh().getValue();
        int threads = 8;
        var start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    try {
                        authService.refresh(token);
                        return true;
                    } catch (ApiException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long successes = 0;
            for (var result : results) {
                successes += result.get() ? 1 : 0;
            }
            assertThat(successes).isLessThanOrEqualTo(1);
        }
        UUID familyActive = jdbc.sql("""
                SELECT family_id FROM refresh_token WHERE token_hash = ?
                """).param(OpaqueTokens.sha256(token)).query(UUID.class).single();
        long active = jdbc.sql("SELECT count(*) FROM refresh_token WHERE family_id = ? AND revoked_at IS NULL")
                .param(familyActive).query(Long.class).single();
        assertThat(active).as("como mucho una sesión viva").isLessThanOrEqualTo(1);
    }
}
