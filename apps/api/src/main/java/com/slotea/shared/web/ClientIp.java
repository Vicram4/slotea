package com.slotea.shared.web;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del cliente para el límite de peticiones, a partir de {@code X-Forwarded-For} tal y como llega
 * a producción (medido en la fase 10, ver {@code docs/fase-10-calidad.md}):
 *
 * <ul>
 * <li>El frontal de Cloud Run <strong>añade</strong> al final la IP que se le conecta: la última entrada
 *     es siempre fiable. Lo que vaya antes lo puede escribir el cliente.</li>
 * <li>Por Vercel, la cabecera llega como {@code [visitante, Vercel]}: Vercel descarta la que mande el
 *     navegador y pone la del visitante, y Google añade la de Vercel.</li>
 * </ul>
 *
 * <p>Por eso: con dos entradas o más, la penúltima (el visitante según Vercel); con una, esa. Quien
 * llame directamente a Cloud Run puede inventarse la penúltima y saltarse el límite por IP: para eso
 * están los límites globales de {@link RateLimiter}. Cerrarlo del todo pediría un secreto compartido
 * entre Vercel y la API (decisión documentada en el ADR de límites).
 *
 * <p>Se lee la petición <em>original</em>: con {@code forward-headers-strategy: framework}, el
 * {@code ForwardedHeaderFilter} de Spring envuelve la petición y oculta {@code X-Forwarded-For}.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        HttpServletRequest raw = unwrap(request);
        String header = raw.getHeader("X-Forwarded-For");
        if (header != null) {
            List<String> hops = Arrays.stream(header.split(",")).map(String::strip).filter(h -> !h.isEmpty())
                    .toList();
            if (!hops.isEmpty()) {
                return hops.get(Math.max(0, hops.size() - 2));
            }
        }
        return raw.getRemoteAddr();
    }

    private static HttpServletRequest unwrap(HttpServletRequest request) {
        ServletRequest current = request;
        while (current instanceof ServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest inner) {
            current = inner;
        }
        return (HttpServletRequest) current;
    }
}
