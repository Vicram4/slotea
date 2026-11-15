package com.slotea.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;

/**
 * Lo que ha llegado a Mailpit, leído por su API REST. Cada test usa direcciones únicas, así que
 * basta con filtrar por destinatario (no hace falta vaciar la bandeja entre tests).
 */
public final class Mailpit {

    private final MailpitContainer container;

    public Mailpit(MailpitContainer container) {
        this.container = container;
    }

    /** Un email tal y como lo ve el destinatario. */
    public record Message(String subject, String from, List<String> to, List<String> replyTo, String text,
            String html) {
    }

    /** Emails enviados a esa dirección, del más reciente al más antiguo. */
    public List<Message> to(String address) {
        String found = client().get().uri("/api/v1/search?query={q}", "to:\"" + address + "\"")
                .retrieve().body(String.class);
        List<String> ids = JsonPath.read(found, "$.messages[*].ID");
        return ids.stream().map(this::message).toList();
    }

    /**
     * Espera (poco: el envío es síncrono, pero Mailpit indexa en segundo plano) hasta que haya
     * {@code count} emails para esa dirección y los devuelve.
     */
    public List<Message> await(String address, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        List<Message> messages = to(address);
        while (messages.size() < count && Instant.now().isBefore(deadline)) {
            pause();
            messages = to(address);
        }
        return messages;
    }

    public Message awaitOne(String address) {
        List<Message> messages = await(address, 1);
        if (messages.size() != 1) {
            throw new AssertionError("Expected 1 email to " + address + " but got " + messages.size());
        }
        return messages.getFirst();
    }

    private Message message(String id) {
        String body = client().get().uri("/api/v1/message/{id}", id).retrieve().body(String.class);
        Map<String, Object> from = JsonPath.read(body, "$.From");
        return new Message(JsonPath.read(body, "$.Subject"), (String) from.get("Address"),
                JsonPath.read(body, "$.To[*].Address"), JsonPath.read(body, "$.ReplyTo[*].Address"),
                JsonPath.read(body, "$.Text"), JsonPath.read(body, "$.HTML"));
    }

    private RestClient client() {
        return RestClient.create(container.apiUrl());
    }

    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
