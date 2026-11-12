package com.slotea.business;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsBySlug(String slug);

    /** {@code business} no lleva {@code @TenantId}: se puede buscar sin tenant (páginas públicas). */
    Optional<Business> findBySlug(String slug);
}
