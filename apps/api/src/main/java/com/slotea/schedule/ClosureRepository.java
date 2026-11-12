package com.slotea.schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Filtrado por el negocio de la sesión ({@code @TenantId}). */
public interface ClosureRepository extends JpaRepository<Closure, UUID> {

    /** Cierres que tocan el periodo [from, to] (fechas inclusivas). */
    @Query("""
            select c from Closure c
            where c.endDate >= :from and c.startDate <= :to
            order by c.startDate, c.endDate, c.id
            """)
    List<Closure> findOverlapping(LocalDate from, LocalDate to);

    @Query("""
            select c from Closure c
            where c.staffId = :staffId and c.endDate >= :from and c.startDate <= :to
            order by c.startDate, c.endDate, c.id
            """)
    List<Closure> findOverlappingForStaff(UUID staffId, LocalDate from, LocalDate to);
}
