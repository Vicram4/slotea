# ADR 0004 · Sesión con JWT en cookie `httpOnly` y la API detrás de Vercel

- **Estado:** aceptada (fase 4)
- **Detalle:** [fase 4 § Sesión](../fase-4-identidad.md#sesión-jwt-en-cookie--refresh-rotatorio)

## Contexto

La web (Vercel) y la API (Cloud Run) están en dominios distintos. Hace falta una sesión que un XSS no
pueda robar y que no obligue a configurar CORS ni cookies de terceros (que los navegadores ya bloquean).

## Decisión

- **El navegador solo habla con Vercel.** `/api/*` llega a la API por un `rewrite` de Next: las cookies
  son *first-party* y no hay CORS.
- **JWT de acceso de 15 min** (HS256) y **refresh token rotatorio** de 30 días, los dos en cookies
  `HttpOnly; Secure; SameSite=Lax`. En la BD, solo el SHA-256 del refresh.
- **Rotación con detección de robo:** presentar un refresh ya rotado revoca toda la familia.
- **CSRF con `csrf.spa()`**, también con el JWT. El resource server de Spring excluye del CSRF las
  peticiones con bearer token (tiene sentido con `Authorization`, no con una cookie), así que
  `SecurityConfig` lo vuelve a exigir; un test impide que se pierda.

## Alternativas descartadas

- **Token en `localStorage`:** un XSS lo lee.
- **Sesión con estado en el servidor:** la API escala a cero y a varias instancias; habría que guardarla en la BD.
- **Llamadas directas a la API con CORS:** las cookies serían de terceros.

## Consecuencias

- Revocar el acceso a alguien tarda como mucho 15 min (lo que dura su JWT): las membresías se releen en cada refresh.
- La web renueva la sesión de forma *single-flight*: varias peticiones con 401 comparten un solo refresh.
- El rewrite se evalúa al construir: cambiar `API_URL` obliga a redesplegar la web.
