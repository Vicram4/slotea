package com.slotea.catalog;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.catalog.ServiceDtos.ServiceRequest;
import com.slotea.catalog.ServiceDtos.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Servicios del negocio activo. STAFF los ve; solo OWNER los cambia. No se borran: se desactivan. */
@Tag(name = "services")
@RestController
@RequestMapping("/api/v1/services")
class ServiceController {

    private final CatalogService catalog;

    ServiceController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Operation(summary = "Servicios del negocio (activos e inactivos)")
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    List<ServiceResponse> list() {
        return catalog.list();
    }

    @Operation(summary = "Un servicio")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    ServiceResponse get(@PathVariable UUID id) {
        return catalog.get(id);
    }

    @Operation(summary = "Crear un servicio (solo el dueño)")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) {
        ServiceResponse created = catalog.create(request);
        return ResponseEntity.created(URI.create("/api/v1/services/" + created.id())).body(created);
    }

    @Operation(summary = "Editar un servicio, también para activarlo o desactivarlo (solo el dueño)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) {
        return catalog.update(id, request);
    }
}
