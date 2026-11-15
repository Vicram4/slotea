package com.slotea.notification;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slotea.notification.EmailDispatcher.DeliveryReport;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Reintento de los emails pendientes. Lo llama un cron externo (GitHub Actions) con la cabecera
 * {@code X-Jobs-Token}: como la API escala a cero, un {@code @Scheduled} no se ejecutaría.
 * Fuera del contrato OpenAPI: la web no lo usa.
 */
@Hidden
@RestController
class OutboxJobController {

    /** Tope por llamada, para no acercarse al timeout de Cloud Run (60 s). */
    static final int MAX_PER_RUN = 100;

    private final EmailDispatcher dispatcher;

    OutboxJobController(EmailDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/internal/jobs/outbox")
    DeliveryReport deliverDue() {
        return dispatcher.deliverDue(MAX_PER_RUN);
    }
}
