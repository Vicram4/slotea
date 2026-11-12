package com.slotea.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /** {@code email} ya normalizado ({@link AppUser#normalizeEmail}). */
    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
