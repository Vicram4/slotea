package com.slotea.business;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import com.slotea.auth.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Pertenencia de un usuario a un negocio, con su rol.
 *
 * <p>{@code @TenantId}: Hibernate filtra por el negocio de la petición en cada consulta y lo
 * rellena al insertar. Un negocio no puede leer las membresías de otro aunque conozca sus ids.
 */
@Entity
@Table(name = "membership")
public class Membership {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Membership() {
    }

    /** Membresía del negocio de la petición (Hibernate rellena {@code business_id}). */
    public Membership(AppUser user, Role role, Instant createdAt) {
        this.user = user;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** Solo para operaciones de sistema (registro de un negocio nuevo): fija el negocio a mano. */
    public static Membership forBusiness(UUID businessId, AppUser user, Role role, Instant createdAt) {
        var membership = new Membership(user, role, createdAt);
        membership.businessId = businessId;
        return membership;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public AppUser getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
