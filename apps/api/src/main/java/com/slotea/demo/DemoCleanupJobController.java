package com.slotea.demo;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Borra las copias de la demo caducadas. Lo llama el cron externo (GitHub Actions, {@code jobs.yml})
 * con la cabecera {@code X-Jobs-Token}, en la misma llamada horaria que la outbox: no despierta la BD
 * más veces. Fuera del contrato OpenAPI.
 */
@Hidden
@RestController
class DemoCleanupJobController {

    private final DemoService demo;

    DemoCleanupJobController(DemoService demo) {
        this.demo = demo;
    }

    @PostMapping("/internal/jobs/demo-cleanup")
    Map<String, Integer> cleanup() {
        return Map.of("deleted", demo.deleteExpired());
    }
}
