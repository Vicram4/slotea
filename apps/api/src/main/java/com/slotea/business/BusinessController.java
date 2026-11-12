package com.slotea.business;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.business.BusinessDtos.BusinessResponse;
import com.slotea.business.BusinessDtos.MemberResponse;
import com.slotea.business.BusinessDtos.UpdateBusinessRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** El negocio activo de la sesión. STAFF puede verlo; solo OWNER lo cambia y ve el equipo. */
@Tag(name = "business")
@RestController
@RequestMapping("/api/v1/business")
class BusinessController {

    private final BusinessService service;

    BusinessController(BusinessService service) {
        this.service = service;
    }

    @Operation(summary = "Datos del negocio activo")
    @GetMapping
    @PreAuthorize("hasRole('STAFF')")
    BusinessResponse get() {
        return service.current();
    }

    @Operation(summary = "Cambiar los datos del negocio (solo el dueño)")
    @PatchMapping
    @PreAuthorize("hasRole('OWNER')")
    BusinessResponse update(@Valid @RequestBody UpdateBusinessRequest request) {
        return service.update(request);
    }

    @Operation(summary = "Miembros del negocio (solo el dueño)")
    @GetMapping("/members")
    @PreAuthorize("hasRole('OWNER')")
    List<MemberResponse> members() {
        return service.members();
    }

    @Operation(summary = "Un miembro del negocio (solo el dueño)")
    @GetMapping("/members/{userId}")
    @PreAuthorize("hasRole('OWNER')")
    MemberResponse member(@PathVariable UUID userId) {
        return service.member(userId);
    }
}
