package com.slotea.shared.web;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

/**
 * Límite de peticiones en memoria (token bucket de Bucket4j). Vale porque la API corre en una sola
 * instancia ({@code max-instances=1}); con varias, los contadores tendrían que ir a un almacén común.
 *
 * <p>Cada regla tiene un límite <strong>por IP</strong> y, en las rutas caras o que envían emails, otro
 * <strong>global</strong>: es la red de seguridad para quien se salte el límite por IP (ver
 * {@link ClientIp}). Una petición cuenta en todas las reglas que encajan; basta con que una diga que no.
 */
public final class RateLimiter {

    /** Por encima de esto se vacían los contadores por IP (los reinicia, pero acota la memoria). */
    static final int MAX_TRACKED_KEYS = 50_000;

    /** Una ventana: {@code requests} peticiones cada {@code period}, que se van recargando poco a poco. */
    public record Limit(int requests, Duration period) {
    }

    public record Rule(String name, String method, Pattern path, Limit perIp, @Nullable Limit global) {

        boolean matches(String requestMethod, String requestPath) {
            return (method.equals("*") || method.equalsIgnoreCase(requestMethod))
                    && path.matcher(requestPath).matches();
        }
    }

    private static final String SLUG = "[^/]+";

    /** Las reglas de producción. Las cifras son holgadas para una persona y cortas para un script. */
    public static final List<Rule> DEFAULT_RULES = List.of(
            new Rule("login", "POST", Pattern.compile("/api/v1/auth/login"),
                    new Limit(10, Duration.ofMinutes(1)), new Limit(300, Duration.ofMinutes(1))),
            new Rule("signup", "POST", Pattern.compile("/api/v1/auth/signup"),
                    new Limit(5, Duration.ofHours(1)), new Limit(100, Duration.ofHours(1))),
            new Rule("demo", "POST", Pattern.compile("/api/v1/demo/sessions"),
                    new Limit(20, Duration.ofHours(1)), new Limit(300, Duration.ofHours(1))),
            // Reservar y gestionar una cita envía emails a direcciones que escribe el cliente.
            new Rule("booking", "POST", Pattern.compile("/api/v1/public/businesses/" + SLUG + "/appointments"),
                    new Limit(10, Duration.ofHours(1)), new Limit(300, Duration.ofHours(1))),
            new Rule("manage", "POST",
                    Pattern.compile("/api/v1/public/businesses/" + SLUG + "/appointments/manage/" + SLUG
                            + "/(cancel|reschedule)"),
                    new Limit(20, Duration.ofHours(1)), new Limit(300, Duration.ofHours(1))),
            new Rule("api", "*", Pattern.compile("/api/.*"), new Limit(300, Duration.ofMinutes(1)), null));

    private final List<Rule> rules;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /**
     * Cuenta la petición. Vacío si puede pasar; si no, los segundos que tiene que esperar (para
     * {@code Retry-After}, al menos 1).
     */
    public OptionalLong tryAcquire(String method, String path, String clientIp) {
        long waitNanos = 0;
        for (Rule rule : rules) {
            if (!rule.matches(method, path)) {
                continue;
            }
            long ipWait = consume(rule.name() + "|ip|" + clientIp, rule.perIp());
            // El global solo cuenta lo que ha pasado el límite por IP: si no, un solo abusador lo agotaría
            // para todos los demás.
            long globalWait = ipWait == 0 && rule.global() != null ? consume(rule.name() + "|global", rule.global()) : 0;
            waitNanos = Math.max(waitNanos, Math.max(ipWait, globalWait));
        }
        if (waitNanos == 0) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(1, Duration.ofNanos(waitNanos).toSeconds() + 1));
    }

    /** 0 si había ficha; si no, cuánto falta para la siguiente. */
    private long consume(String key, Limit limit) {
        if (buckets.size() >= MAX_TRACKED_KEYS) {
            buckets.clear();
        }
        Bucket bucket = buckets.computeIfAbsent(key, _ -> Bucket.builder()
                .addLimit(l -> l.capacity(limit.requests()).refillGreedy(limit.requests(), limit.period()))
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return probe.isConsumed() ? 0 : probe.getNanosToWaitForRefill();
    }
}
