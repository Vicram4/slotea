package com.slotea.schedule;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Filtrado por el negocio de la sesión ({@code @TenantId}). */
public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

    List<WorkingHours> findByStaffIdOrderByDayOfWeekAscStartTimeAsc(UUID staffId);

    List<WorkingHours> findByStaffIdIn(Collection<UUID> staffIds);
}
