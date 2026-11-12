package com.slotea.schedule;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Días sin servicio, en fechas locales del negocio y ambas inclusivas. Sin {@code staffId} cierra
 * todo el negocio (festivo, vacaciones); con {@code staffId} es la ausencia de un profesional.
 */
@Entity
@Table(name = "closure")
public class Closure {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "staff_id", updatable = false)
    private @Nullable UUID staffId;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(updatable = false)
    private @Nullable String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Closure() {
    }

    public Closure(@Nullable UUID staffId, LocalDate startDate, LocalDate endDate, @Nullable String reason,
            Instant createdAt) {
        this.staffId = staffId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public @Nullable UUID getStaffId() {
        return staffId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public @Nullable String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
