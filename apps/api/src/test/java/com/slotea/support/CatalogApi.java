package com.slotea.support;

import static org.assertj.core.api.Assertions.assertThat;
import static com.slotea.support.SpaCsrf.spaCsrf;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;
import com.slotea.support.AuthApi.Session;

/** Atajos para crear servicios, profesionales y cierres por la API real en los tests. */
public final class CatalogApi {

    private final MockMvcTester mvc;

    public CatalogApi(MockMvcTester mvc) {
        this.mvc = mvc;
    }

    public UUID createService(Session owner, String name) {
        return created(send(owner, "POST", "/api/v1/services", """
                {"name": "%s", "durationMin": 30, "bufferMin": 5, "priceCents": 1500}
                """.formatted(name)));
    }

    public UUID createStaff(Session owner, String displayName, @Nullable UUID userId) {
        String user = userId == null ? "null" : "\"" + userId + "\"";
        return created(send(owner, "POST", "/api/v1/staff", """
                {"displayName": "%s", "userId": %s}
                """.formatted(displayName, user)));
    }

    public UUID createClosure(Session owner, @Nullable UUID staffId, String startDate, String endDate) {
        String staff = staffId == null ? "null" : "\"" + staffId + "\"";
        return created(send(owner, "POST", "/api/v1/closures", """
                {"staffId": %s, "startDate": "%s", "endDate": "%s", "reason": "Vacaciones"}
                """.formatted(staff, startDate, endDate)));
    }

    /** Petición con sesión, CSRF y cuerpo JSON (si lo hay). */
    public MvcTestResult send(Session session, String method, String uri, @Nullable String json) {
        var request = switch (method) {
            case "GET" -> mvc.get().uri(uri);
            case "POST" -> mvc.post().uri(uri);
            case "PUT" -> mvc.put().uri(uri);
            case "DELETE" -> mvc.delete().uri(uri);
            default -> throw new IllegalArgumentException(method);
        };
        request = request.with(spaCsrf()).cookie(session.access());
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return request.exchange();
    }

    private static UUID created(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(JsonPath.read(AuthApi.body(result), "$.id"));
    }
}
