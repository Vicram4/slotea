package com.slotea.business;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotea.business.StaffDtos.StaffRequest;
import com.slotea.business.StaffDtos.StaffResponse;
import com.slotea.catalog.OfferingRepository;
import com.slotea.shared.web.ApiException;

/**
 * Profesionales del negocio de la petición. Cualquier id de otro negocio (en la ruta o en el
 * cuerpo) da 404, igual que uno que no existe: no revelamos nada.
 */
@Service
@Transactional(readOnly = true)
public class StaffService {

    private final StaffMemberRepository staff;
    private final StaffServiceLinks links;
    private final MembershipRepository memberships;
    private final OfferingRepository offerings;

    StaffService(StaffMemberRepository staff, StaffServiceLinks links, MembershipRepository memberships,
            OfferingRepository offerings) {
        this.staff = staff;
        this.links = links;
        this.memberships = memberships;
        this.offerings = offerings;
    }

    List<StaffResponse> list() {
        Map<UUID, List<UUID>> services = links.serviceIdsByStaff();
        return staff.findAllOrdered().stream()
                .map(s -> StaffResponse.from(s, services.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    StaffResponse get(UUID id) {
        return response(require(id));
    }

    @Transactional
    StaffResponse create(StaffRequest r) {
        UUID userId = checkMember(r.userId());
        var member = new StaffMember(r.displayName().strip(), userId, r.active() == null || r.active(), Instant.now());
        // saveAndFlush: si ese usuario ya tiene ficha, el 409 sale aquí y no al hacer commit.
        return StaffResponse.from(staff.saveAndFlush(member), List.of());
    }

    @Transactional
    StaffResponse update(UUID id, StaffRequest r) {
        StaffMember member = require(id);
        member.update(r.displayName().strip(), checkMember(r.userId()), r.active() == null || r.active());
        staff.saveAndFlush(member);
        return response(member);
    }

    @Transactional
    StaffResponse replaceServices(UUID id, List<UUID> serviceIds) {
        StaffMember member = require(id);
        Set<UUID> unique = new LinkedHashSet<>(serviceIds);
        if (!unique.isEmpty() && offerings.countByIdIn(unique) != unique.size()) {
            throw ApiException.notFound("Alguno de esos servicios no existe.");
        }
        links.replace(member.getId(), unique);
        return response(member);
    }

    /** Para otros módulos (horarios, cierres): el profesional del negocio actual, o 404. */
    public StaffMember require(UUID id) {
        return staff.findById(id).orElseThrow(() -> ApiException.notFound("No existe ese profesional."));
    }

    private StaffResponse response(StaffMember member) {
        return StaffResponse.from(member, links.serviceIds(member.getId()));
    }

    private @Nullable UUID checkMember(@Nullable UUID userId) {
        if (userId != null && memberships.findByUserId(userId).isEmpty()) {
            throw ApiException.notFound("No existe ese miembro.");
        }
        return userId;
    }
}
