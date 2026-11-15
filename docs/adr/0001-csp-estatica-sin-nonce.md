# ADR 0001 · CSP estática, sin nonce

- **Estado:** aceptada (fase 10, 2026-11-15)
- **Código:** [`apps/web/src/lib/security-headers.ts`](../../apps/web/src/lib/security-headers.ts), aplicado en `next.config.ts` (`headers()`).

## Contexto

La web (Next 16 en Vercel) no enviaba ninguna cabecera de seguridad propia. Las respuestas de la API
ya llevan las de Spring Security y llegan al navegador a través del rewrite de `/api/*`.

Next mete en cada página scripts inline (el arranque de React y los datos del render). Para una
Content-Security-Policy sin `'unsafe-inline'` en `script-src` hay dos caminos:

1. **Nonce por petición** (lo que propone la documentación de Next): el proxy genera un nonce y Next lo
   añade a sus scripts. Obliga a renderizar **cada página en cada petición**: se pierde el prerender
   estático y parcial de `cacheComponents`, que usamos en todas las rutas.
2. **Hashes**: no sirven, el contenido de los scripts inline cambia en cada build y en cada página.

## Decisión

CSP **estática** con `script-src 'self' 'unsafe-inline'`, y todo lo demás cerrado al propio origen:

| Directiva | Valor | Por qué |
|---|---|---|
| `default-src` | `'self'` | Nada de terceros: no hay analítica, CDNs ni fuentes externas. |
| `script-src` | `'self' 'unsafe-inline'` (+ `'unsafe-eval'` solo en desarrollo, para Turbopack) | Ver contexto. |
| `style-src` | `'self' 'unsafe-inline'` | Atributos `style` de React (el color de marca va en variables CSS) y el HTML de los emails en la bandeja de la demo. |
| `connect-src` | `'self'` (+ `ws:` en desarrollo) | El navegador solo habla con Vercel; la API va por `/api`. |
| `img-src` | `'self' data: blob:` | |
| `font-src` | `'self'` | `next/font` sirve Inter desde el propio dominio. |
| `frame-src` | `'self'` | El iframe `srcdoc` de la bandeja hereda esta política. |
| `frame-ancestors` | `'none'` | Nadie puede meter la app en un iframe (clickjacking). |
| `object-src` | `'none'` | |
| `base-uri` / `form-action` | `'self'` | |
| `upgrade-insecure-requests` | solo en producción | |

Además: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (navegadores antiguos),
`Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` sin cámara, micrófono,
geolocalización, pagos ni USB, y HSTS de dos años. La página para gestionar una cita lleva el token
en la URL, así que envía `Referrer-Policy: no-referrer` (además de su `<meta name="referrer">`).

## Consecuencias

- **Lo que protege:** cargar scripts o enviar datos a otro dominio (exfiltración), clickjacking,
  `<base>` y formularios hacia fuera, MIME sniffing y fugas del token por el `Referer`.
- **Lo que no protege:** un XSS que consiga inyectar un `<script>` inline se ejecutaría. La defensa
  ahí es React (escapa todo lo que pinta; no usamos `dangerouslySetInnerHTML`) y que el HTML de los
  emails solo se muestre en un iframe `sandbox` sin `allow-scripts`. Las cookies de sesión son
  `httpOnly`, así que un script inyectado tampoco podría leerlas.
- **Comprobado** con la build de producción: portada, panel (agenda, ajustes, bandeja con un email
  real), página pública de reservas y login, sin ninguna violación en la consola.
- **Si algún día** se necesita una CSP estricta (p. ej. contenido de usuarios en HTML), el cambio es
  pasar a nonces en el proxy de Next y aceptar el render dinámico.
