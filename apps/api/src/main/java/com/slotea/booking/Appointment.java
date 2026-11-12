package com.slotea.booking;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Una cita. Las horas son instantes (UTC); {@code blockedUntil} = fin + margen del servicio.
 *
 * <p>Que dos citas del mismo profesional no se solapen no lo garantiza esta clase, sino la
 * restricción {@code appointment_no_overlap} de la BD (ver {@code V4__booking.sql}).
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "blocked_until", nullable = false)
    private Instant blockedUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(name = "manage_token_hash", nullable = false)
    private byte[] manageTokenHash;

    private @Nullable String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private @Nullable Instant cancelledAt;

    @Version
    private int version;

    protected Appointment() {
    }

    /** Cita confirmada del negocio de la petición (Hibernate rellena {@code business_id}). */
    public Appointment(UUID staffId, UUID serviceId, UUID customerId, Instant startsAt, Instant endsAt,
            Instant blockedUntil, byte[] manageTokenHash, @Nullable String notes, Instant createdAt) {
        this.staffId = staffId;
        this.serviceId = serviceId;
        this.customerId = customerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.blockedUntil = blockedUntil;
        this.status = AppointmentStatus.CONFIRMED;
        this.manageTokenHash = manageTokenHash;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
