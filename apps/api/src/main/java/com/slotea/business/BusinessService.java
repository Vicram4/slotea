package com.slotea.business;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotea.business.BusinessDtos.BusinessResponse;
import com.slotea.business.BusinessDtos.MemberResponse;
import com.slotea.business.BusinessDtos.UpdateBusinessRequest;
import com.slotea.shared.tenancy.TenantContext;
import com.slotea.shared.web.ApiException;

/**
 * Siempre trabaja sobre el negocio de la petición (claim {@code bid} del JWT). Nunca recibe un
 * {@code businessId} del cliente: así es imposible pedir datos de otro negocio.
 */
@Service
@Transactional(readOnly = true)
class BusinessService {

    private final BusinessRepository businesses;
    private final MembershipRepository memberships;

    BusinessService(BusinessRepository businesses, MembershipRepository memberships) {
        this.businesses = businesses;
        this.memberships = memberships;
    }

    BusinessResponse current() {
        return BusinessResponse.from(currentBusiness());
    }

    @Transactional
    BusinessResponse update(UpdateBusinessRequest request) {
        Business business = currentBusiness();
        if (request.name() != null) {
            business.rename(request.name().strip());
        }
        if (request.timezone() != null) {
            business.changeTimezone(request.timezone());
        }
        if (request.contactEmail() != null) {
            business.changeContactEmail(request.contactEmail().strip().toLowerCase(Locale.ROOT));
        }
        return BusinessResponse.from(business);
    }

    /** {@code @TenantId} filtra por negocio: no hace falta (ni se debe) añadir {@code where business_id}. */
    List<MemberResponse> members() {
        return memberships.findAllWithUser().stream().map(MemberResponse::from).toList();
    }

    /** Un usuario de otro negocio da 404, igual que uno que no existe: no revelamos nada. */
    MemberResponse member(UUID userId) {
        return memberships.findByUserId(userId)
                .map(MemberResponse::from)
                .orElseThrow(() -> ApiException.notFound("No existe ese miembro."));
    }

    private Business currentBusiness() {
        return businesses.findById(TenantContext.requireBusinessId())
                .orElseThrow(() -> ApiException.notFound("El negocio ya no existe."));
    }
}
