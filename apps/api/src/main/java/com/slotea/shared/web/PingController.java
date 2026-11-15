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
            String xff = request.getHeader("X-Forwarded-For");
            List<String> hops = xff == null ? List.of()
                    : Arrays.stream(xff.split(",")).map(String::strip).toList();
            String realIp = request.getHeader("X-Real-IP");
            log.info("client-ip diag={} xffEntries={} realIpPresent={} realIpIndexFromRight={} remoteAddrInXff={}",
                    diag.replaceAll("[^a-z0-9-]", ""), hops.size(), realIp != null,
                    realIp == null ? -1 : hops.size() - 1 - hops.lastIndexOf(realIp.strip()),
                    hops.contains(request.getRemoteAddr()));
        }
        return new PingResponse("ok", "slotea-api", Instant.now(clock));
    }

    public record PingResponse(String status, String service, Instant time) {
    }
}
