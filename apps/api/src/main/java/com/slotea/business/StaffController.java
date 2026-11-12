package com.slotea.business;

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

import com.slotea.business.StaffDtos.StaffRequest;
import com.slotea.business.StaffDtos.StaffResponse;
import com.slotea.business.StaffDtos.StaffServicesRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Profesionales del negocio activo. STAFF los ve; solo OWNER los cambia. No se borran: se desactivan. */
@Tag(name = "staff")
@RestController
@RequestMapping("/api/v1/staff")
class StaffController {

    private final StaffService service;

    StaffController(StaffService service) {
        this.service = service;
    }

    @Operation(summary = "Profesionales del negocio (activos e inactivos)")
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    List<StaffResponse> list() {
        return service.list();
    }

    @Operation(summary = "Un profesional")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STAFF')")
    StaffResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Crear un profesional (solo el dueño)")
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<StaffResponse> create(@Valid @RequestBody StaffRequest request) {
        StaffResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/staff/" + created.id())).body(created);
    }

    @Operation(summary = "Editar un profesional, también para activarlo o desactivarlo (solo el dueño)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    StaffResponse update(@PathVariable UUID id, @Valid @RequestBody StaffRequest request) {
        return service.update(id, request);
    }

    @Operation(summary = "Sustituir los servicios que hace un profesional (solo el dueño)")
    @PutMapping("/{id}/services")
    @PreAuthorize("hasRole('OWNER')")
    StaffResponse replaceServices(@PathVariable UUID id, @Valid @RequestBody StaffServicesRequest request) {
        return service.replaceServices(id, request.serviceIds());
    }
}
