package com.slotea.business;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    boolean existsBySlug(String slug);
}
