package com.slotea.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Filtrado por el negocio de la sesión ({@code @TenantId}). */
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Citas que ocupan a estos profesionales en algún momento de [from, to). Mismo criterio que la
     * restricción de la BD: se cuenta hasta {@code blockedUntil} y las canceladas no ocupan.
     */
    @Query("""
            select a from Appointment a
            where a.staffId in :staffIds
              and a.status <> com.slotea.booking.AppointmentStatus.CANCELLED
              and a.startsAt < :to and a.blockedUntil > :from
            order by a.startsAt
            """)
    List<Appointment> findBlocking(Collection<UUID> staffIds, Instant from, Instant to);
}
