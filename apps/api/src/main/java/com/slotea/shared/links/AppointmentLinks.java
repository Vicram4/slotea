package com.slotea.shared.links;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.slotea.shared.security.OpaqueTokens;

/**
 * Enlaces para que un invitado gestione su cita sin cuenta: {@code {web}/{slug}/cita/{token}}.
 *
 * <p><strong>El token es un HMAC del id de la cita</strong> con una clave del servidor
 * ({@code LINK_SECRET}), no un valor aleatorio:
 * <ul>
 * <li>En la BD solo se guarda su SHA-256 ({@code appointment.manage_token_hash}), como el resto de tokens.</li>
 * <li>Se puede <em>recalcular</em> cuando se envía el email (también en un reintento horas después),
 *     así que nunca hace falta guardarlo en claro, ni en la outbox.</li>
 * <li>Con una copia de la BD no basta para fabricar enlaces: hace falta además la clave.</li>
 * <li>Al cambiar la hora, la cita conserva su id y su enlace.</li>
 * </ul>
 * Contrapartida: cambiar la clave invalida todos los enlaces enviados.
 */
@Component
public class AppointmentLinks {

    private static final String ALGORITHM = "HmacSHA256";
    /** Separa este uso de la clave de cualquier otro que pudiera tener en el futuro. */
    private static final String PURPOSE = "appointment-manage-link:";

    private final SecretKeySpec key;
    private final String webUrl;

    AppointmentLinks(LinkProperties properties) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.secret().strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LINK_SECRET must be Base64", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("LINK_SECRET must decode to at least 32 bytes (256 bits)");
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
        this.webUrl = properties.webUrl().toString().replaceAll("/+$", "");
    }

    /** Token del enlace (Base64url, 43 caracteres). No se guarda en ningún sitio. */
    public String token(UUID appointmentId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((PURPOSE + appointmentId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available", e);
        }
    }

    /** Lo que se guarda en la BD y por lo que se busca la cita. */
    public byte[] tokenHash(UUID appointmentId) {
        return OpaqueTokens.sha256(token(appointmentId));
    }

    /** Página para ver, cancelar o cambiar la cita. */
    public URI manageUrl(String slug, UUID appointmentId) {
        return URI.create(webUrl + "/" + slug + "/cita/" + token(appointmentId));
    }

    /** La agenda del panel en ese día (fecha local del negocio). */
    public URI agendaUrl(LocalDate day) {
        return URI.create(webUrl + "/panel/agenda?date=" + day);
    }

    /** Página pública de reservas del negocio. */
    public URI bookingUrl(String slug) {
        return URI.create(webUrl + "/" + slug);
    }
}
