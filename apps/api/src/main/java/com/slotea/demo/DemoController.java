package com.slotea.demo;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.auth.AuthCookies;
import com.slotea.auth.AuthDtos.MeResponse;
import com.slotea.auth.AuthService.Session;
import com.slotea.demo.DemoDtos.DemoSessionRequest;
import com.slotea.demo.DemoDtos.DemoSwitchRequest;
import com.slotea.shared.tenancy.TenantContext;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Botones «Entrar como…» de la portada y de la barra de la demo. Las cookies, como en el login. */
@Tag(name = "demo")
@RestController
@RequestMapping("/api/v1/demo")
class DemoController {

    private final DemoService demo;
    private final AuthCookies cookies;

    DemoController(DemoService demo, AuthCookies cookies) {
        this.demo = demo;
        this.cookies = cookies;
    }

    /**
     * Público (sin sesión), pero con CSRF como cualquier POST. 400 si se pide entrar como empleado en un
     * sector que no lo tiene; 503 si hay demasiadas copias vivas.
     */
    @Operation(summary = "Crear una copia de la demo y entrar en ella")
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    MeResponse create(@Valid @RequestBody DemoSessionRequest request, HttpServletResponse response) {
        return open(demo.create(request.sector(), request.role()), response);
    }

    /** Solo dentro de una copia de la demo (404 en un negocio real). */
    @Operation(summary = "Cambiar de dueño a empleado (o al revés) en la misma copia de la demo")
    @PostMapping("/switch")
    @PreAuthorize("hasRole('STAFF')")
    MeResponse switchRole(@Valid @RequestBody DemoSwitchRequest request, HttpServletResponse response) {
        return open(demo.switchTo(TenantContext.requireBusinessId(), request.role()), response);
    }

    private MeResponse open(Session session, HttpServletResponse response) {
        cookies.write(response, session.tokens());
        return session.me();
    }
}
