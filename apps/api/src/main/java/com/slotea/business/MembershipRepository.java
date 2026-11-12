package com.slotea.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Todas las consultas van filtradas por el negocio de la sesión ({@code @TenantId}): no hace falta
 * (ni se debe) pasar el {@code businessId} como parámetro.
 */
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Query("select m from Membership m join fetch m.user order by m.createdAt, m.id")
    List<Membership> findAllWithUser();

    @Query("select m from Membership m join fetch m.user where m.user.id = :userId")
    Optional<Membership> findByUserId(UUID userId);

    /**
     * Negocios de un usuario. Cruza negocios: solo tiene sentido dentro de
     * {@link com.slotea.shared.tenancy.SystemTransactions} (con un tenant normal devolvería como mucho uno).
     */
    @Query("""
            select new com.slotea.business.MembershipSummary(b.id, b.slug, b.name, m.role)
            from Membership m join Business b on b.id = m.businessId
            where m.user.id = :userId
            order by m.createdAt, m.id
            """)
    List<MembershipSummary> findSummariesByUserId(UUID userId);
}
