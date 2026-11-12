package com.slotea.booking;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotea.booking.PublicDtos.PublicBusinessResponse;
import com.slotea.booking.PublicDtos.PublicService;
import com.slotea.booking.PublicDtos.PublicStaff;
import com.slotea.business.Business;
import com.slotea.business.BusinessRepository;
import com.slotea.business.StaffService;
import com.slotea.business.StaffService.ActiveStaff;
import com.slotea.catalog.Offering;
import com.slotea.catalog.OfferingRepository;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;

/**
 * La cabecera de la página pública: solo lo que se puede reservar. Un servicio activo que no hace
 * ningún profesional activo no aparece (no tendría huecos), ni un profesional sin servicios.
 */
@Service
@Transactional(readOnly = true)
class PublicBusinessService {

    private final BusinessRepository businesses;
    private final OfferingRepository offerings;
    private final StaffService staffService;

    PublicBusinessService(BusinessRepository businesses, OfferingRepository offerings, StaffService staffService) {
        this.businesses = businesses;
        this.offerings = offerings;
        this.staffService = staffService;
    }

    PublicBusinessResponse current() {
        Business business = businesses.findById(TenantContext.requireBusinessId())
                .orElseThrow(() -> ApiException.notFound("No existe ese negocio."));
        Set<UUID> activeServices = new HashSet<>();
        List<Offering> services = offerings.findAllOrdered().stream().filter(Offering::isActive).toList();
        services.forEach(s -> activeServices.add(s.getId()));

        List<PublicStaff> staff = staffService.activeWithServices().stream()
                .map(s -> toPublic(s, activeServices))
                .filter(s -> !s.serviceIds().isEmpty())
                .toList();
        Set<UUID> bookable = new HashSet<>();
        staff.forEach(s -> bookable.addAll(s.serviceIds()));

        return new PublicBusinessResponse(business.getSlug(), business.getName(), business.getTimezone(),
                services.stream()
                        .filter(s -> bookable.contains(s.getId()))
                        .map(s -> new PublicService(s.getId(), s.getName(), s.getDescription(), s.getDurationMin(),
                                s.getPriceCents()))
                        .toList(),
                staff);
    }

    private static PublicStaff toPublic(ActiveStaff s, Set<UUID> activeServices) {
        return new PublicStaff(s.member().getId(), s.member().getDisplayName(),
                s.serviceIds().stream().filter(activeServices::contains).toList());
    }
}
