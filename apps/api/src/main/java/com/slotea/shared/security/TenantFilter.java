package com.slotea.shared.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.slotea.shared.tenancy.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Va justo después de validar el JWT: fija el negocio del claim {@code bid} como tenant durante el
 * resto de la petición. Sin JWT (o sin negocio), no fija nada y Hibernate no ve datos de ningún negocio.
 */
final class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String businessId = authentication instanceof JwtAuthenticationToken jwt
                ? jwt.getToken().getClaimAsString(AuthClaims.BUSINESS_ID)
                : null;
        if (businessId == null) {
            chain.doFilter(request, response);
            return;
        }
        ScopedValue.CallableOp<Void, Exception> rest = () -> {
            chain.doFilter(request, response);
            return null;
        };
        try {
            TenantContext.callAs(UUID.fromString(businessId), rest);
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
}
