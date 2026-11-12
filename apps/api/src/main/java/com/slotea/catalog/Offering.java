package com.slotea.catalog;

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
 * Un servicio que se puede reservar (tabla {@code service}; se llama {@code Offering} para no
 * confundirlo con el {@code @Service} de Spring).
 *
 * <p>No se borra: se desactiva. Las citas (fase 6) lo referencian y el historial debe seguir intacto.
 */
@Entity
@Table(name = "service")
public class Offering {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(nullable = false)
    private String name;

    private @Nullable String description;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "buffer_min", nullable = false)
    private int bufferMin;

    @Column(name = "price_cents")
    private @Nullable Integer priceCents;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Offering() {
    }

    /** Servicio del negocio de la petición (Hibernate rellena {@code business_id}). */
    public Offering(String name, @Nullable String description, int durationMin, int bufferMin,
            @Nullable Integer priceCents, boolean active, Instant createdAt) {
        update(name, description, durationMin, bufferMin, priceCents, active);
        this.createdAt = createdAt;
    }

    public void update(String name, @Nullable String description, int durationMin, int bufferMin,
            @Nullable Integer priceCents, boolean active) {
        this.name = name;
        this.description = description;
        this.durationMin = durationMin;
        this.bufferMin = bufferMin;
        this.priceCents = priceCents;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBusinessId() {
        return businessId;
    }

    public String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }

    public int getDurationMin() {
        return durationMin;
    }

    public int getBufferMin() {
        return bufferMin;
    }

    public @Nullable Integer getPriceCents() {
        return priceCents;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
