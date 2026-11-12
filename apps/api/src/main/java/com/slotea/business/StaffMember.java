package com.slotea.business;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Profesional que atiende citas. Puede estar enlazado a un usuario del panel ({@code userId}) o no
 * (alguien que no usa la app). La BD exige que ese usuario sea miembro del mismo negocio.
 *
 * <p>No se borra: se desactiva (las citas lo referencian).
 */
@Entity
@Table(name = "staff_member")
public class StaffMember {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "user_id")
    private @Nullable UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StaffMember() {
    }

    /** Profesional del negocio de la petición (Hibernate rellena {@code business_id}). */
    public StaffMember(String displayName, @Nullable UUID userId, boolean active, Instant createdAt) {
        update(displayName, userId, active);
        this.createdAt = createdAt;
    }

    public void update(String displayName, @Nullable UUID userId, boolean active) {
        this.displayName = displayName;
        this.userId = userId;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public @Nullable UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
