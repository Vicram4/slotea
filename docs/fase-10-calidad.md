# Fase 10 — Calidad y lanzamiento

Lo que convierte el MVP en algo que se puede enseñar y dejar en producción sin vigilarlo: mantenimiento
automático, límites de peticiones, rediseño, cabeceras de seguridad, tests de punta a punta,
accesibilidad y una revisión de seguridad.

## Mantenimiento que no depende de nadie

- **Latido semanal del email** (`maintenance.yml`): Brevo desactiva una clave SMTP tras 90 días sin uso
  y la demo no envía emails nunca. Una vez por semana la API se escribe un email a sí misma; si el SMTP
  lo rechaza, el job falla y GitHub avisa.
- **Keepalive de los cron**: GitHub apaga los workflows programados tras 60 días sin actividad.
- **Dependabot** mensual y agrupado (un PR por ecosistema); las alertas de seguridad llegan aparte.

## Límites de peticiones

`RateLimiter` (Bucket4j, en memoria) va como filtro **antes** de Spring Security: una ráfaga se corta sin
tocar la BD ni calcular un BCrypt. Responde 429 en `ProblemDetail` con `Retry-After`. Cada regla tiene un
límite por IP (holgado para una persona, corto para un script) y otro global:

| Regla | Por IP | Global |
|---|---|---|
| Login | 10 / min | 300 / min |
| Registro | 5 / h | 100 / h |
| Copias de la demo | 20 / h | 300 / h |
| Reservar (envía emails) | 10 / h | 300 / h |
| Cambiar o cancelar con el enlace | 20 / h | 300 / h |
| Resto de `/api` | 300 / min | — |

**De dónde sale la IP** (`ClientIp`), medido en producción: el frontal de Cloud Run **añade** al final de
`X-Forwarded-For` la IP que se le conecta. Por Vercel llega `[visitante, Vercel]`. Se usa la penúltima
entrada si hay dos o más, y si no, la única. Quien llame **directamente** a Cloud Run puede inventarse esa
penúltima entrada y esquivar el límite por IP, pero no el global. Cerrarlo del todo pediría un secreto
compartido entre Vercel y la API: es una mejora anotada, no imprescindible con los límites globales.

## Cabeceras de seguridad

CSP estática (sin nonce), `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy` (y `no-referrer` en la
página con el token de la cita), `Permissions-Policy` y HSTS. Por qué estática y qué cubre:
[ADR 0001](adr/0001-csp-estatica-sin-nonce.md).

## Tests de punta a punta (Playwright)

`apps/web/e2e/`, contra la app de verdad (web + API + PostgreSQL). En la CI, el job `e2e` arranca
PostgreSQL como servicio, la API desde su jar y la build de producción de la web. Si falla, no hay
despliegue.

| Recorrido | Qué demuestra |
|---|---|
| Un cliente reserva y cancela con su enlace | El flujo principal, sin cuenta, con el token de la cita. |
| Dos clientes van a por el mismo hueco | El segundo recibe un 409 y la página le pide otra hora. |
| El dueño cancela desde la agenda | El email al cliente aparece en la bandeja de la demo. |
| Un empleado entra en el panel | Solo ve lo suyo, y lo decide la API (no el menú). |
| Móvil | La navegación se pliega en un menú. |
| Registro, salir y volver a entrar | Cuenta nueva con su lista de «qué falta». |

La carrera de verdad (dos inserciones simultáneas contra la restricción `EXCLUDE`) la prueban los tests de
la API con Testcontainers: en un navegador no se puede forzar que coincidan en el mismo milisegundo.

## Accesibilidad

`e2e/accessibility.spec.ts` pasa **axe** (WCAG 2.2 A y AA) por las páginas públicas, todo el recorrido de
reserva (también la confirmación y la página para gestionar la cita) y cada página del panel. Lo que
encontró y se arregló:

- **Gris sobre gris**: el texto secundario sobre fondos grises o sobre el tinte del color de marca no
  llegaba a 4,5:1.
- **Nombre accesible distinto del texto visible**: los botones de cita decían «09:30–09:50, Laura…»
  mientras se veía «09:30 Laura». Quien usa control por voz dice lo que ve; ahora el nombre empieza por eso.

axe solo encuentra una parte de los problemas; el resto (orden del foco, lectores de pantalla) se ha
revisado a mano en los componentes: foco al cambiar de paso, `aria-live` en los resúmenes, diálogos de
Radix.

## Revisión de seguridad

| Punto | Estado |
|---|---|
| Aislamiento entre negocios | `@TenantId` de Hibernate + FKs compuestas; cada endpoint tiene su test «A no ve nada de B» (404). |
| Sesión | JWT de 15 min + refresh rotatorio hasheado, en cookies `httpOnly; Secure; SameSite=Lax`. |
| CSRF | `csrf.spa()`, también con el JWT en cookie (el resource server lo excluiría). |
| Contraseñas | BCrypt; se rechazan las de más de 72 bytes en vez de truncarlas. |
| Token de la cita | HMAC del id; en la BD solo su hash; se busca por hash. Página con `no-referrer` y `noindex`. |
| Token de los jobs | Comparación en tiempo constante (`MessageDigest.isEqual`). |
| Emails | Thymeleaf escapa todo (`th:text`); los enlaces los construye el servidor. |
| HTML de los emails en la bandeja | iframe `sandbox` sin `allow-scripts` ni `allow-same-origin`. |
| Redirecciones | Ninguna toma la URL de un parámetro. |
| Demo | Cambiar de rol solo en una copia de la demo (404 en un negocio real); slugs `demo-*` reservados. |
| Lo expuesto | Actuator solo `health` (sin detalles). La documentación OpenAPI es pública a propósito: el código también lo es. |
| Dependencias | `pnpm audit --prod` sin vulnerabilidades; Dependabot y sus alertas de seguridad. |

**Riesgos aceptados**:
- `script-src 'unsafe-inline'` (ver el ADR 0001).
- Saltarse el límite por IP llamando directamente a Cloud Run (lo frena el límite global).
- El límite vive en memoria: cada instancia de Cloud Run cuenta lo suyo. Con `max-instances=2`, como
  mucho se duplica; si creciera, iría a la BD o a Redis.
