package com.slotea.shared.web;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/** Endpoint mínimo para comprobar de punta a punta que el frontend llega a la API. */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    // TEMPORAL (fase 10): mide cómo llega la IP del cliente a través de Vercel y Cloud Run, para diseñar
    // el límite de peticiones. No registra ninguna IP, solo su posición. Se quita en cuanto se mida.
    private static final Logger log = LoggerFactory.getLogger(PingController.class);

    private final Clock clock;

    public PingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    public PingResponse ping(HttpServletRequest request) {
        String diag = request.getHeader("X-Slotea-Diag");
        if (diag != null) {
            // ForwardedHeaderFilter (forward-headers-strategy: framework) oculta X-Forwarded-For: se lee
            // la petición original que envuelve.
            jakarta.servlet.ServletRequest raw = request;
            while (raw instanceof jakarta.servlet.ServletRequestWrapper wrapper) {
                raw = wrapper.getRequest();
            }
            var rawRequest = (HttpServletRequest) raw;
            String xff = rawRequest.getHeader("X-Forwarded-For");
            List<String> hops = xff == null ? List.of()
                    : Arrays.stream(xff.split(",")).map(String::strip).toList();
            String realIp = rawRequest.getHeader("X-Real-IP");
            log.info("client-ip diag={} xffEntries={} spoofIndex={} realIpIndex={} realIpIsSpoof={} "
                    + "remoteAddrIsSpoof={} rawRemoteAddrIndex={}",
                    diag.replaceAll("[^a-z0-9-]", ""), hops.size(), hops.indexOf("203.0.113.7"),
                    realIp == null ? -2 : hops.indexOf(realIp.strip()),
                    realIp != null && realIp.startsWith("203.0.113."),
                    request.getRemoteAddr().equals("203.0.113.7"), hops.indexOf(rawRequest.getRemoteAddr()));
        }
        return new PingResponse("ok", "slotea-api", Instant.now(clock));
    }

    public record PingResponse(String status, String service, Instant time) {
    }
}
