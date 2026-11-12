package com.slotea.business;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Filtrado por el negocio de la sesión ({@code @TenantId}): nunca recibe un {@code businessId}. */
public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    @Query("select s from StaffMember s order by s.active desc, lower(s.displayName), s.id")
    List<StaffMember> findAllOrdered();
}
