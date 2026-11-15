package com.slotea.shared.links;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.slotea.shared.security.OpaqueTokens;

class AppointmentLinksTest {

    static final String SECRET = "dGVzdC1vbmx5LWxpbmstc2VjcmV0LW5vdC11c2VkLWluLXByb2R1Y3Rpb24hIQ==";
    static final String OTHER_SECRET = "b3RoZXItdGVzdC1vbmx5LWxpbmstc2VjcmV0LW5vdC1mb3ItcHJvZHVjdGlvbg==";

    final AppointmentLinks links = links(SECRET);
    final UUID appointment = UUID.randomUUID();

    @Test
    void theTokenCanBeRecomputedSoItNeverHasToBeStored() {
        assertThat(links.token(appointment))
                .isEqualTo(links(SECRET).token(appointment))
                .hasSize(43) // 256 bits en Base64url sin relleno
                .matches("[A-Za-z0-9_-]+");
        assertThat(links.tokenHash(appointment)).isEqualTo(OpaqueTokens.sha256(links.token(appointment)));
    }

    @Test
    void eachAppointmentAndEachSecretGiveADifferentToken() {
        assertThat(links.token(appointment)).isNotEqualTo(links.token(UUID.randomUUID()));
        assertThat(links.token(appointment)).as("sin la clave no se puede fabricar el enlace")
                .isNotEqualTo(links(OTHER_SECRET).token(appointment));
    }

    @Test
    void urlsPointToTheWebWithoutDoubleSlashes() {
        var withSlash = new AppointmentLinks(new LinkProperties(SECRET, URI.create("https://web.example.test/")));

        assertThat(withSlash.manageUrl("barberia", appointment))
                .hasToString("https://web.example.test/barberia/cita/" + links.token(appointment));
        assertThat(withSlash.bookingUrl("barberia")).hasToString("https://web.example.test/barberia");
    }

    @Test
    void weakOrMalformedSecretsStopTheApplication() {
        assertThatThrownBy(() -> links("c2hvcnQ=")).hasMessageContaining("at least 32 bytes");
        assertThatThrownBy(() -> links("not base64!")).hasMessageContaining("Base64");
    }

    private static AppointmentLinks links(String secret) {
        return new AppointmentLinks(new LinkProperties(secret, URI.create("https://web.example.test")));
    }
}
