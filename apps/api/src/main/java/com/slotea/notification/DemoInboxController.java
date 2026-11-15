package com.slotea.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.notification.EmailRenderer.RenderedEmail;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bandeja de la demo: los emails que habría mandado una copia de la demo (al cliente y al negocio),
 * que nunca se envían de verdad (estado {@code DEMO}). Se renderizan al pedirlos con las mismas
 * plantillas que el envío real, así que se ve exactamente lo que recibiría cada uno.
 *
 * <p>Solo el dueño: los emails van al cliente y al email de contacto del negocio, no a los empleados.
 * En un negocio real responde 404.
 */
@Tag(name = "demo")
@RestController
@RequestMapping("/api/v1/demo/inbox")
@PreAuthorize("hasRole('OWNER')")
class DemoInboxController {

    static final int LIMIT = 50;
    private static final String NOT_FOUND = "No hay ningún email con ese id en la bandeja de la demo.";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final EmailRenderer renderer;

    DemoInboxController(JdbcClient jdbc, JsonMapper json, EmailRenderer renderer) {
        this.jdbc = jdbc;
        this.json = json;
        this.renderer = renderer;
    }

    /** {@code toBusiness}: aviso al negocio (si no, es el email que recibe el cliente). */
    public record InboxEmail(UUID id, EmailType type, boolean toBusiness, String to, String subject,
            Instant createdAt, @Nullable UUID appointmentId) {
    }

    public record InboxEmailDetail(UUID id, EmailType type, boolean toBusiness, String to, String subject,
            Instant createdAt, @Nullable UUID appointmentId, String html, String text) {
    }

    private record Row(UUID id, EmailType type, String to, String payload, Instant createdAt,
            @Nullable UUID appointmentId) {

        RenderedEmail render(JsonMapper json, EmailRenderer renderer) {
            return renderer.render(type, json.readValue(payload, AppointmentEmail.class));
        }
    }

    @Operation(summary = "Bandeja de la demo: los últimos emails de esta copia (nunca se envían)")
    @GetMapping
    List<InboxEmail> list() {
        return rows(null).stream().map(row -> new InboxEmail(row.id(), row.type(), row.type().toBusiness(), row.to(),
                row.render(json, renderer).subject(), row.createdAt(), row.appointmentId())).toList();
    }

    @Operation(summary = "Un email de la bandeja de la demo, renderizado (HTML y texto)")
    @GetMapping("/{id}")
    InboxEmailDetail get(@PathVariable UUID id) {
        Row row = rows(id).stream().findFirst().orElseThrow(() -> ApiException.notFound(NOT_FOUND));
        RenderedEmail email = row.render(json, renderer);
        return new InboxEmailDetail(row.id(), row.type(), row.type().toBusiness(), row.to(), email.subject(),
                row.createdAt(), row.appointmentId(), email.html(), email.text());
    }

    /** Solo los del negocio de la petición y solo si es una demo (si no, 404 como un recurso ajeno). */
    private List<Row> rows(@Nullable UUID onlyId) {
        UUID businessId = TenantContext.requireBusinessId();
        boolean demo = jdbc.sql("SELECT is_demo FROM business WHERE id = ?").param(businessId)
                .query(Boolean.class).optional().orElse(false);
        if (!demo) {
            throw ApiException.notFound("La bandeja solo existe en la demo.");
        }
        return jdbc.sql("""
                SELECT id, type, to_email, CAST(payload AS text) AS payload, created_at, appointment_id
                FROM email_outbox
                WHERE business_id = :businessId AND status = 'DEMO'
                  AND (CAST(:onlyId AS uuid) IS NULL OR id = CAST(:onlyId AS uuid))
                ORDER BY created_at DESC, id DESC
                LIMIT :limit""")
                .param("businessId", businessId)
                .param("onlyId", onlyId)
                .param("limit", LIMIT)
                .query((rs, _) -> new Row(rs.getObject("id", UUID.class), EmailType.valueOf(rs.getString("type")),
                        rs.getString("to_email"), rs.getString("payload"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("appointment_id", UUID.class)))
                .list();
    }
}
