package com.slotea.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slotea.catalog.ServiceDtos.ServiceRequest;
import com.slotea.catalog.ServiceDtos.ServiceResponse;
import com.slotea.shared.web.ApiException;

/** Servicios del negocio de la petición. Un id de otro negocio da 404, igual que uno inexistente. */
@Service
@Transactional(readOnly = true)
class CatalogService {

    private final OfferingRepository offerings;

    CatalogService(OfferingRepository offerings) {
        this.offerings = offerings;
    }

    List<ServiceResponse> list() {
        return offerings.findAllOrdered().stream().map(ServiceResponse::from).toList();
    }

    ServiceResponse get(UUID id) {
        return ServiceResponse.from(find(id));
    }

    @Transactional
    ServiceResponse create(ServiceRequest r) {
        var offering = new Offering(r.name().strip(), clean(r.description()), r.durationMin(), orZero(r.bufferMin()),
                r.priceCents(), r.active() == null || r.active(), Instant.now());
        // saveAndFlush: si el nombre ya existe, el 409 sale aquí y no al hacer commit.
        return ServiceResponse.from(offerings.saveAndFlush(offering));
    }

    @Transactional
    ServiceResponse update(UUID id, ServiceRequest r) {
        Offering offering = find(id);
        offering.update(r.name().strip(), clean(r.description()), r.durationMin(), orZero(r.bufferMin()),
                r.priceCents(), r.active() == null || r.active());
        return ServiceResponse.from(offerings.saveAndFlush(offering));
    }

    private Offering find(UUID id) {
        return offerings.findById(id).orElseThrow(() -> ApiException.notFound("No existe ese servicio."));
    }

    private static @Nullable String clean(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static int orZero(@Nullable Integer value) {
        return value == null ? 0 : value;
    }
}
