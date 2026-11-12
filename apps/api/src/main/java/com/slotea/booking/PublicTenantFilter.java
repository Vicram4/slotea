package com.slotea.booking;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.slotea.business.Business;
import com.slotea.business.BusinessRepository;
import com.slotea.shared.tenancy.TenantContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo mismo que {@code TenantFilter} hace con el JWT del panel, pero para las páginas públicas: fija
 * como tenant el negocio del {@code slug} de la ruta durante el resto de la petición.
 *
 * <p>Así, los servicios y repositorios no saben (ni les importa) si la petición viene del panel o de
 * la página pública: {@code @TenantId} filtra igual. Un slug que no existe responde 404 aquí mismo.
 */
final class PublicTenantFilter extends OncePerRequestFilter {

    static final String URL_PATTERN = "/api/v1/public/businesses/*";
    private static final PathPattern PATH =
            PathPatternParser.defaultInstance.parse("/api/v1/public/businesses/{slug}/**");

    private final BusinessRepository businesses;
    private final JsonMapper json;

    PublicTenantFilter(BusinessRepository businesses, JsonMapper json) {
        this.businesses = businesses;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var match = PATH.matchAndExtract(PathContainer.parsePath(request.getRequestURI()));
        // business no lleva @TenantId: se puede buscar por slug sin tenant.
        Optional<UUID> businessId = match == null
                ? Optional.empty()
                : businesses.findBySlug(match.getUriVariables().get("slug")).map(Business::getId);
        if (businessId.isEmpty()) {
            notFound(request, response);
            return;
        }
        ScopedValue.CallableOp<Void, Exception> rest = () -> {
            chain.doFilter(request, response);
            return null;
        };
        try {
            TenantContext.callAs(businessId.get(), rest);
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void notFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No existe ese negocio.");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }

    /** Se registra como filtro de servlet, después de la cadena de Spring Security. */
    @Configuration(proxyBeanMethods = false)
    static class Registration {

        @Bean
        FilterRegistrationBean<PublicTenantFilter> publicTenantFilter(BusinessRepository businesses, JsonMapper json) {
            var registration = new FilterRegistrationBean<>(new PublicTenantFilter(businesses, json));
            registration.addUrlPatterns(URL_PATTERN);
            return registration;
        }
    }
}
