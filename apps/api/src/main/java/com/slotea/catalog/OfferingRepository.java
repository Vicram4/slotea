package com.slotea.catalog;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Filtrado por el negocio de la sesión ({@code @TenantId}): nunca recibe un {@code businessId}. */
public interface OfferingRepository extends JpaRepository<Offering, UUID> {

    /** Activos primero y luego por nombre: es el orden en que los quiere ver el dueño. */
    @Query("select o from Offering o order by o.active desc, lower(o.name), o.id")
    List<Offering> findAllOrdered();

    /** Cuántos de estos ids existen en el negocio actual (los de otro negocio no cuentan). */
    @Query("select count(o) from Offering o where o.id in :ids")
    long countByIdIn(Collection<UUID> ids);
}
