# Fase 7 — Emails y gestión de la cita

Al reservar, el cliente recibe un email de confirmación con un enlace. Desde ese enlace puede **ver, cancelar o cambiar la cita sin cuenta**. Cada cambio manda su email. En local los emails los recoge Mailpit; en producción salen por Brevo.

## Outbox: ningún email perdido, ninguno de más

```
POST /appointments ──► transacción: INSERT appointment + INSERT email_outbox ──► COMMIT
                                                                                    │
                       envío inmediato (fuera de la transacción) ◄──────────────────┘
                       └─ si falla: sigue PENDING ──► cron cada hora ──► POST /internal/jobs/outbox
```

- **El email se guarda en la misma transacción que la cita** (migración `V5`, tabla `email_outbox`).
  - Si la reserva hace rollback, no sale ningún email.
  - Si se confirma, el email no se pierde aunque el SMTP esté caído.
- **El envío inmediato va después del commit y en la misma petición**, no en segundo plano:
  - Cloud Run quita la CPU en cuanto responde, así que un hilo aparte podría quedarse congelado.
  - Tampoco se usa `@TransactionalEventListener(AFTER_COMMIT)`: en ese momento la transacción aún no ha soltado su conexión, y el envío ocuparía una segunda. El pool de producción es de 5.
- **Reclamar antes de enviar:**
  - Una sentencia corta (`UPDATE … FROM (SELECT … FOR UPDATE SKIP LOCKED)`) suma un intento y aplaza `next_attempt_at` 2 minutos (*lease*).
  - El SMTP se llama **fuera** de la transacción.
  - Dos ejecuciones a la vez se reparten los emails. Si la instancia muere a mitad, el email vuelve a estar disponible cuando vence el lease.
- **Reintentos:** a 1 min, 5 min, 30 min y 2 h. Tras 5 intentos queda `FAILED`. El cron pasa cada hora, así que esas esperas son un mínimo.
- **Entrega *al menos una vez*:** si el SMTP acepta el email pero la instancia muere antes de marcarlo `SENT`, saldrá duplicado. La alternativa (*como mucho una vez*) perdería emails.
- **El health de la API no depende del SMTP** (`management.health.mail.enabled: false`). Un Brevo lento no puede hacer que Cloud Run o la prueba de humo den la API por muerta.

### El job (`/internal/jobs/outbox`)
- **Quién lo llama:** `.github/workflows/jobs.yml`, cada hora, con la cabecera `X-Jobs-Token`.
  - No uso `@Scheduled` porque la API escala a cero.
  - Cada hora y no más a menudo, porque cada llamada despierta Neon.
- **Seguridad:**
  - Cadena de Spring Security propia, sin cookies ni CSRF (la credencial es una cabecera que el navegador no añade solo).
  - La comparación del token es en tiempo constante.
  - No es `Authorization: Bearer`, porque el resource server lo leería como un JWT.
- Queda fuera del contrato OpenAPI (`@Hidden`).

## El enlace de la cita

`{web}/{slug}/cita/{token}`. El token es un **HMAC-SHA256 del id de la cita** con `LINK_SECRET`:

| Propiedad | Por qué |
|---|---|
| En la BD solo está su SHA-256 (`manage_token_hash`) | Como el resto de tokens |
| Se recalcula al enviar | Un reintento horas después no necesita el token en claro, ni en la outbox |
| Con una copia de la BD no basta | Hace falta además la clave |
| Cambiar la hora no cambia el enlace | La cita conserva su id (el UUID lo genera la aplicación para poder derivar el hash antes del `INSERT`) |

Inconveniente: cambiar `LINK_SECRET` invalida todos los enlaces enviados.

La respuesta de la reserva también devuelve el token. Así, la confirmación enlaza a «Ver o cambiar mi cita» aunque el email acabe en spam.

## Gestionar la cita (API pública)

`/api/v1/public/businesses/{slug}/appointments/manage/{token}`

| Endpoint | Qué hace |
|---|---|
| `GET` | La cita: estado, servicio, profesional, hora local, si aún se puede cambiar y hasta cuándo |
| `POST /cancel` | La cancela y libera el hueco. Repetirlo no hace nada (ni manda otro email) |
| `GET /availability` | Huecos para moverla. **La propia cita no cuenta como ocupada** (si no, no se podría retrasar 15 min) |
| `POST /reschedule` | La mueve: **misma fila, mismo id, mismo enlace**. Las mismas reglas que una reserva nueva (hueco ofrecido, cola por profesional, reintento con «cualquiera») |

- El token se busca **dentro del negocio del slug**: el enlace de otro negocio da 404, igual que uno inventado.
- **Plazo:** se puede cancelar o cambiar hasta **2 h antes** (`BookingPolicy.CHANGE_DEADLINE`). Después, o con la cita completada o cancelada, la API responde 409 y la página lo explica.
- **Abrir el enlace nunca cambia nada:** los antivirus del correo abren los enlaces. Cancelar es un `POST` con CSRF, tras un diálogo de confirmación.
- **La página no filtra el token:** `Referrer-Policy: no-referrer` y `noindex`. El token sí aparece en los logs de peticiones de Vercel y Cloud Run (está en la ruta); lo asumimos, como cualquier enlace mágico.

## Emails

- Thymeleaf. Cada email lleva HTML (tablas y estilos en línea) **y texto plano** (`templates/email/appointment.{html,txt}`).
- Confirmación, cambio (con la hora anterior tachada) y cancelación (con enlace para reservar otra).
- Fechas en español y en la zona horaria del negocio.
- **Reply-To = email del negocio:** si el cliente responde, le contesta el negocio.
- La outbox guarda los datos del email, no el HTML. La «bandeja de la demo» (fase 9) podrá renderizarlos.

### Configuración

| Variable | Local | Producción |
|---|---|---|
| `LINK_SECRET` | `.env` (obligatoria) | Secret Manager `link-secret` |
| `JOBS_TOKEN` | `.env` (opcional: sin ella, el job da 401) | `jobs-token` + secreto `JOBS_TOKEN` del repo |
| `MAIL_FROM` | `Slotea <reservas@slotea.test>` | `mail-from` (remitente verificado en Brevo). Sin él, la API no arranca |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | — (Mailpit) | `mail-username` / `mail-password` (clave SMTP de Brevo) |
| `APP_WEB_URL` | `http://localhost:3000` | `https://slotea.vercel.app` |

## Problemas encontrados (y sus tests)

- **Mailpit tardaba 10 s en saludar** a través del port-forward de Docker, por la búsqueda DNS inversa del cliente. Se arregla con `MP_SMTP_DISABLE_RDNS` (en `compose.yaml` y en el contenedor de los tests).
- **JavaMail busca el nombre canónico de la máquina** para el `EHLO` y para el `Message-ID`. En este equipo tardaba 14 s por email. Se arregla fijando `mail.smtp.localhost` y `mail.from`.
- **El health de mail tumbaba `/actuator/health`** cuando el SMTP no respondía (lo destaparon `ApiApplicationTests` y `ProdProfileTests`). Desactivado.

## Tests

| Test | Qué demuestra |
|---|---|
| `EmailOutboxTests` (Mailpit real) | Solo se encola dentro de una transacción · rollback = ningún email · envío inmediato · el job envía lo pendiente · **dos jobs a la vez → cada email una vez (1 intento)** · el payload no contiene el token |
| `EmailRetryTests` (SMTP que falla) | La reserva responde 201 aunque el SMTP esté caído · esperas 1/5/30/120 min · `FAILED` tras 5 intentos · no se reintenta antes de tiempo · se recupera cuando vuelve el SMTP |
| `EmailRenderingTests` | Fechas en español **a los dos lados del cambio de hora** · HTML y texto con el enlace · hora anterior en el cambio · cancelación sin enlace de gestión · HTML escapado |
| `AppointmentLinksTest` | Token determinista de 43 caracteres · distinto por cita y por clave · URLs · clave débil = no arranca |
| `PublicBookingApiTests` | El email de confirmación llega a Mailpit con su enlace, cuyo hash es el guardado; Reply-To = negocio |
| `ManageAppointmentApiTests` | Ver · tokens inválidos → 404 · cancelar libera el hueco y es idempotente · CSRF · plazo de 2 h · citas terminadas · mover sobre sí misma · mismo enlace tras el cambio · «cualquiera» · huecos ocupados o fuera de horario → 409 |
| `BookingConcurrencyTests` | **4 citas movidas a la vez al mismo hueco → 1 OK y 3 × 409**, ninguna se pierde |
| `BookingIsolationTests` | El enlace de B por el slug de A → 404 en los 4 endpoints, y B intacto |
| `InternalJobsSecurityTests` | Sin token, token falso, bearer o sesión del panel → 401 · el bueno, sin CSRF → 200 |
| `ProdProfileTests` | En producción el SMTP exige autenticación y STARTTLS |
| Web (Vitest + MSW) | Página de gestión: ver, enlace inválido, cancelar con confirmación, fuera de plazo, cambiar con el mismo profesional, 409 que recarga los huecos · la confirmación enlaza a la gestión |

## Fuera de esta fase
- Recordatorio 24 h antes (fase 11): será otro job sobre esta misma outbox.
- Aviso al negocio de cada reserva: con la agenda (fase 8).
- Bandeja de la demo (fase 9). Limitar las peticiones a los endpoints con token (fase 10).
