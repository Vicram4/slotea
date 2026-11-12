package com.slotea.support;

import static org.assertj.core.api.Assertions.assertThat;
import static com.slotea.support.SpaCsrf.spaCsrf;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Atajos para los tests de integración: registrar negocios, añadir empleados e iniciar sesión por la
 * API real (cookies incluidas). Cada llamada usa emails y slugs únicos: los tests no se pisan.
 */
public final class AuthApi {

    public static final String PASSWORD = "correct-horse-battery";

    private final MockMvcTester mvc;
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public AuthApi(MockMvcTester mvc, JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /** Sesión de un usuario: las dos cookies tal y como las devolvió la API. */
    public record Session(Cookie access, Cookie refresh) {
    }

    /** Un negocio recién registrado con su dueño ya logueado. */
    public record Tenant(UUID businessId, String slug, UUID ownerId, String ownerEmail, Session owner) {
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String signupJson(String businessName, String slug, String fullName, String email) {
        return """
                {"businessName": "%s", "slug": "%s", "fullName": "%s", "email": "%s", "password": "%s"}
                """.formatted(businessName, slug, fullName, email, PASSWORD);
    }

    public Tenant signup(String name) {
        String slug = unique(name);
        String email = slug + "@example.test";
        MvcTestResult result = mvc.post().uri("/api/v1/auth/signup").with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("Negocio " + name, slug, "Dueño " + name, email))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String body = body(result);
        return new Tenant(UUID.fromString(JsonPath.read(body, "$.activeBusiness.businessId")), slug,
                UUID.fromString(JsonPath.read(body, "$.user.id")), email, sessionOf(result));
    }

    /** Crea un empleado directamente en la BD (aún no hay endpoint de invitación) e inicia sesión. */
    public Session addStaff(Tenant tenant, String name) {
        String email = unique(name) + "@example.test";
        UUID userId = UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user (id, email, password_hash, full_name) VALUES (?, ?, ?, ?)")
                .params(userId, email, passwordEncoder.encode(PASSWORD), "Empleado " + name)
                .update();
        jdbc.sql("INSERT INTO membership (id, business_id, user_id, role, created_at) VALUES (?, ?, ?, 'STAFF', ?)")
                .params(UUID.randomUUID(), tenant.businessId(), userId, java.sql.Timestamp.from(Instant.now()))
                .update();
        return login(email, PASSWORD);
    }

    public Session login(String email, String password) {
        MvcTestResult result = loginResult(email, password);
        assertThat(result).hasStatus(HttpStatus.OK);
        return sessionOf(result);
    }

    public MvcTestResult loginResult(String email, String password) {
        return mvc.post().uri("/api/v1/auth/login").with(spaCsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password))
                .exchange();
    }

    public MvcTestResult refresh(Cookie refreshCookie) {
        return mvc.post().uri("/api/v1/auth/refresh").with(spaCsrf()).cookie(refreshCookie).exchange();
    }

    public static Session sessionOf(MvcTestResult result) {
        Cookie access = result.getResponse().getCookie("access_token");
        Cookie refresh = result.getResponse().getCookie("refresh_token");
        assertThat(access).as("access_token cookie").isNotNull();
        assertThat(refresh).as("refresh_token cookie").isNotNull();
        return new Session(access, refresh);
    }

    public static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
