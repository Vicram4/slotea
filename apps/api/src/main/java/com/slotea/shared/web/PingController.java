package com.slotea.shared.web;

import java.time.Clock;
import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint mínimo para comprobar de punta a punta que el frontend llega a la API. */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    private final Clock clock;

    public PingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    public PingResponse ping() {
        return new PingResponse("ok", "slotea-api", Instant.now(clock));
    }

    public record PingResponse(String status, String service, Instant time) {
    }
}
