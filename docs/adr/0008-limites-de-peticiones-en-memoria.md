# ADR 0008 · Límites de peticiones en memoria, por IP y globales

- **Estado:** aceptada (fase 10)
- **Detalle:** [fase 10 § Límites de peticiones](../fase-10-calidad.md#límites-de-peticiones)

## Contexto

Login, registro, la demo y las reservas (que envían emails a direcciones que escribe el cliente) son
públicos. Hay que frenar scripts sin molestar a una persona, con presupuesto cero.

## Decisión

`RateLimiter` con **Bucket4j en memoria**, como filtro antes de Spring Security (una ráfaga no llega a
tocar la BD ni a calcular un BCrypt). Cada regla tiene un límite **por IP** y otro **global**; responde
429 con `Retry-After`.

La IP sale de `X-Forwarded-For` tal y como se midió en producción: el frontal de Cloud Run añade al final
la IP que se le conecta, y por Vercel llega `[visitante, Vercel]`. Se usa la penúltima entrada.

## Alternativas descartadas

- **Redis o la BD como almacén de cubos:** otra pieza o más carga en Neon, para un tráfico de demo.
- **Un secreto compartido Vercel → API**, para que la API solo acepte tráfico de Vercel: cierra la vía de
  falsear `X-Forwarded-For` llamando directamente a Cloud Run. Se deja como mejora; el límite global ya
  acota el daño.

## Consecuencias

- Cada instancia cuenta lo suyo: con `max-instances=2`, como mucho se duplica el límite.
- Un reinicio pone los contadores a cero (aceptable).
- Los tests y el job E2E lo desactivan (`app.rate-limit.enabled=false`): todas sus peticiones salen de la misma IP.
