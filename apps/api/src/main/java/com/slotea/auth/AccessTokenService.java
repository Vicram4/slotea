package com.slotea.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.slotea.business.MembershipSummary;
import com.slotea.shared.security.AuthClaims;

/** Emite el JWT de acceso: usuario ({@code sub}), negocio activo ({@code bid}) y rol en él. */
@Service
class AccessTokenService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;

    AccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    String issue(UUID userId, @Nullable MembershipSummary activeBusiness) {
        Instant now = Instant.now(clock);
        var claims = JwtClaimsSet.builder()
                .issuer(AuthClaims.ISSUER)
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString());
        if (activeBusiness != null) {
            claims.claim(AuthClaims.BUSINESS_ID, activeBusiness.businessId().toString())
                    .claim(AuthClaims.ROLE, activeBusiness.role().name());
        }
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
