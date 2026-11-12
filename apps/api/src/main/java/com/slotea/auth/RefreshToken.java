package com.slotea.auth;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Refresh token rotatorio. Solo se guarda el SHA-256 del valor que viaja en la cookie.
 * Todos los tokens de una sesión comparten {@code familyId}.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "business_id", updatable = false)
    private @Nullable UUID businessId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;

    @Column(name = "replaced_by")
    private @Nullable UUID replacedBy;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, UUID familyId, byte[] tokenHash, @Nullable UUID businessId,
            Instant createdAt, Instant expiresAt) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash.clone();
        this.businessId = businessId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public @Nullable UUID getBusinessId() {
        return businessId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public @Nullable Instant getRevokedAt() {
        return revokedAt;
    }

    public @Nullable UUID getReplacedBy() {
        return replacedBy;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
