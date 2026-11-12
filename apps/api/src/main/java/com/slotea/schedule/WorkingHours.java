package com.slotea.schedule;

import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un tramo del horario semanal de un profesional, en hora local del negocio. Varios tramos el
 * mismo día = jornada partida. La BD impide que dos tramos del mismo día se solapen ({@code EXCLUDE}).
 */
@Entity
@Table(name = "working_hours")
public class WorkingHours {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @TenantId
    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private UUID staffId;

    /** ISO-8601: 1 = lunes … 7 = domingo. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkingHours() {
    }

    public WorkingHours(UUID staffId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.staffId = staffId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() {
        return id;
    }

    public UUID getStaffId() {
        return staffId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
