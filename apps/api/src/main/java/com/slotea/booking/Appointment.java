package com.slotea.booking;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.id.uuid.UuidVersion7Strategy;
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

    /**
     * Lo genera la aplicación ({@link #newId()}), no Hibernate: el hash del token del enlace se
     * deriva del id y tiene que estar listo antes del INSERT (ver {@code AppointmentLinks}).
     */
    @Id
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    /** Cambia si al reprogramar con «cualquier profesional» le toca a otro. */
    @Column(name = "staff_id", nullable = false)
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

    /** {@code Integer} y no {@code int}: {@code null} = cita nueva (así lo decide Spring Data, ya que el id viene puesto). */
    @Version
    private @Nullable Integer version;

    protected Appointment() {
    }

    /** UUID v7 (ordenable por fecha), el mismo generador que usa Hibernate en el resto de entidades. */
    public static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }

    /** Cita confirmada del negocio de la petición (Hibernate rellena {@code business_id}). */
    public Appointment(UUID id, UUID staffId, UUID serviceId, UUID customerId, Instant startsAt, Instant endsAt,
            Instant blockedUntil, byte[] manageTokenHash, @Nullable String notes, Instant createdAt) {
        this.id = id;
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

    public @Nullable Instant getCancelledAt() {
        return cancelledAt;
    }

    /** Libera el hueco al instante (la restricción ignora las canceladas). Repetirlo no cambia nada. */
    void cancel(Instant now) {
        if (status == AppointmentStatus.CANCELLED) {
            return;
        }
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be cancelled");
        }
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /**
     * Cómo acabó una cita que ya ha empezado: {@code COMPLETED}, {@code NO_SHOW}, o {@code CONFIRMED} para
     * deshacer un error. Las reglas (que haya empezado, que no esté cancelada) las comprueba el servicio.
     */
    void recordOutcome(AppointmentStatus outcome) {
        if (status == AppointmentStatus.CANCELLED || outcome == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cancelling is not an outcome");
        }
        this.status = outcome;
    }

    /**
     * Mueve la cita (misma fila, mismo id y, por tanto, mismo enlace). Que el nuevo hueco esté libre
     * lo comprueba la restricción de la BD al hacer flush.
     */
    void moveTo(UUID staffId, Instant startsAt, Instant endsAt, Instant blockedUntil) {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Only confirmed appointments can be moved");
        }
        this.staffId = staffId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.blockedUntil = blockedUntil;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
