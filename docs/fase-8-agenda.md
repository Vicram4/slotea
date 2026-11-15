# Fase 8 — Agenda

El panel ya tiene sentido: el negocio ve sus citas por días o por semanas, las abre, marca cómo fueron y puede cancelarlas. Cada empleado ve **solo su agenda**. Además, el negocio recibe un email cada vez que un cliente reserva, cambia o cancela.

## API (`/api/v1/appointments`)

| Endpoint | Quién | Qué hace |
|---|---|---|
| `GET ?from=&to=&staffId=` | STAFF | Citas que **empiezan** entre dos fechas (incluidas, máx. 42 días), canceladas incluidas. Trae también las columnas (`staff`) y la zona horaria |
| `GET /{id}` | STAFF | Una cita, con los datos de contacto del cliente |
| `PUT /{id}/status` | STAFF | `COMPLETED`, `NO_SHOW` o `CONFIRMED` (deshacer). Solo si **ya ha empezado** |
| `POST /{id}/cancel` | STAFF | Cancela una cita que **aún no ha empezado** y avisa al cliente. Repetirlo no hace nada |

Cada cita trae `canCancel` y `canRecordOutcome`: la interfaz no repite las reglas, las lee.

### Qué ve cada uno
- **El dueño:** todo el negocio. Puede filtrar por profesional.
- **Un empleado:** solo las citas de **su ficha de profesional** (la que el dueño ha enlazado a su usuario en la fase 5).
  - Lo decide el **servidor** a partir del JWT (`Viewer`), no un filtro que mande el navegador.
  - La cita de otro profesional da **404**, igual que la de otro negocio: no revelamos que existe.
  - Pedir la agenda de otro profesional (`staffId`) da **403**: la lista de profesionales sí la puede ver.
  - Sin ficha enlazada, su agenda está vacía y la web le explica qué hacer.

### Reglas de las acciones
| Estado | Antes de empezar | Ya empezada |
|---|---|---|
| Confirmada | Cancelar (email al cliente) | Marcar completada / no presentada |
| Completada / No presentada | — | Cambiar entre ellas o deshacer |
| Cancelada | — (repetir no hace nada) | — |

- El negocio cancela **sin el límite de 2 h** que tiene el enlace del cliente.
- Marcar cómo fue **no manda emails**.
- Un cambio simultáneo (p. ej. el cliente mueve la cita mientras el dueño la cancela) choca con `@Version` → **409** «La cita acaba de cambiar».

### Los días, en la zona del negocio
«El 25 de octubre» va de las 00:00 a las 00:00 del día 26 **en Madrid**, aunque ese día dure 25 horas. Se calcula con `LocalDate.atStartOfDay(zone)` y hay un test con el cambio de hora.

## Avisos al negocio

Cuando **el cliente** reserva, cambia o cancela, el negocio recibe un email en su `contact_email`:

- Van por **la misma outbox** que los del cliente, en la misma transacción (migración `V6`: nuevos tipos en el `CHECK`).
- Primero se envía el del cliente (el que espera la persona que acaba de hacer clic) y después el del negocio. Si uno falla, lo reintenta el job.
- **Reply-To = el cliente:** si el negocio responde, le escribe a él.
- Llevan un botón a **la agenda de ese día** (`/panel/agenda?date=…`), nunca el enlace de gestión del cliente.
- Lo que hace el propio negocio desde el panel no le genera aviso.
- Los **negocios de demostración** (`is_demo`, fase 9) no reciben avisos: su email es ficticio y los rebotes perjudican la reputación del remitente en Brevo.

`AppointmentNotifications` reúne «qué emails genera cada cambio»: lo usan la reserva, el enlace del cliente y la agenda.

## Ausencias del empleado
Un empleado puede **crear y borrar sus propias ausencias** (pendiente desde la fase 5). Las de otros y los cierres del negocio los ve en solo lectura (403 si lo intenta por la API).

## Web (`/panel/agenda`)

- **Día:** una columna por profesional sobre una rejilla horaria, como mínimo de 08:00 a 20:00 (se amplía si hay citas fuera).
- **Semana:** de lunes a domingo, de **un** profesional (el elegido o el primero del equipo).
- **Móvil:** la rejilla no cabe; se muestra como lista agrupada por días.
- **Colores por estado.** Una cita ya empezada sin marcar lleva borde discontinuo, para que no se olvide.
- **Línea roja de «ahora»** en la columna de hoy, en la hora del negocio (no la del navegador).
- Las canceladas se ocultan por defecto («Mostrar canceladas»). Si coinciden con la cita que ocupó su hueco, van lado a lado.
- **La URL guarda lo que se ve** (`?date=2026-11-23&view=week&staff=…`): el enlace de los avisos abre el día correcto y el botón «atrás» funciona.
- Al pulsar una cita se abre el detalle (email y teléfono clicables) con las acciones. Cancelar pide confirmación porque el cliente recibe un email. Si la API responde 409, se muestra su explicación.
- Se refresca cada minuto y al volver a la pestaña. Al cambiar de día se mantiene la agenda anterior hasta que llega la nueva (sin parpadeos).
- **Inicio del panel:** tarjeta «Hoy» con las citas del día.

### Trampa: `useSearchParams` y `cacheComponents`
Como `usePathname`, bloquea el prerender si no va dentro de un `Suspense`. `AgendaRoute` (lee y escribe la URL) va dentro de uno en `app/panel/agenda/page.tsx`; `AgendaView` recibe la ubicación por props y se testea sin router.

## Tests

- **`AgendaApiTests`:**
  - Qué ve el dueño y qué ve un empleado (enlazado o no).
  - Validación del rango.
  - Los días en la semana del cambio de hora.
  - Transiciones de estado y cancelación (email al cliente, ninguno al negocio, hueco libre otra vez).
  - CSRF.
  - Avisos al negocio (asunto, Reply-To, sin el token del cliente) y que los negocios demo no los reciben.
- **`AgendaIsolationTests`:** el dueño de A con ids de B en cada endpoint → 404 y B intacto. Cada endpoint nuevo añade su fila.
- **`StaffAbsencesTests`:** el empleado gestiona sus ausencias y nada más.
- **`EmailRenderingTests`:** plantilla del aviso (día local, no UTC) y que ningún aviso lleva el enlace del cliente.
- **Web (Vitest + MSW):** rejilla de día y de semana, navegación, canceladas, marcar y deshacer, cancelar con confirmación, error 409, vista del empleado, lista del móvil, tarjeta «Hoy», ausencias propias y utilidades de fechas.

## Fuera de esta fase
- **Invitar empleados** (email con token para crear su cuenta). La vista del empleado está hecha y probada; en la fase 9 el seed de la demo creará un empleado ficticio.
- **Crear citas desde el panel** (reservas por teléfono). Reutilizaría `BookingService` tal cual.
- Vista de mes, arrastrar citas para moverlas y desactivar los avisos al negocio.
