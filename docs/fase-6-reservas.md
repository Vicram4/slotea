# Fase 6 — Disponibilidad y reserva

Un cliente entra en `/{slug}`, elige servicio, profesional (o «cualquiera»), día y hora, y reserva como invitado con su nombre y su email. **La doble reserva es imposible, y lo demuestran tests con concurrencia real.**

## Modelo de datos (migración `V4`)

```
customer     ficha de cliente por negocio; el email (en minúsculas) la identifica    UNIQUE (business_id, email)
appointment  staff_id, service_id, customer_id (FKs compuestas)
             starts_at, ends_at, blocked_until (= fin + margen), timestamptz en UTC
             status CONFIRMED | CANCELLED | COMPLETED | NO_SHOW, manage_token_hash, notes, version
```

La regla que impide la doble reserva vive en la BD:

```sql
CONSTRAINT appointment_no_overlap EXCLUDE USING gist (
    business_id WITH =, staff_id WITH =,
    tstzrange(starts_at, blocked_until, '[)') WITH &&
) WHERE (status <> 'CANCELLED')
```

- Rechaza cualquier solape, también el parcial. Con `'[)'`, dos citas seguidas no se solapan. Las citas canceladas liberan el hueco.
- Las FKs a profesional, servicio y cliente no llevan `ON DELETE`: profesionales y servicios se desactivan, no se borran. Son `NO ACTION` y no `RESTRICT`, porque `NO ACTION` se comprueba al final de la sentencia. Así, borrar el negocio entero (por ejemplo, en el reset de la demo de la fase 9) sí funciona en cascada.
- `manage_token_hash` ya se genera. El enlace para gestionar la cita se envía por email en la fase 7.

## Cálculo de huecos

`AvailabilityCalculator` (`schedule`) es una **función pura**: no usa la BD, Spring ni el reloj. `AvailabilityService` (`booking`) le pasa los horarios, los cierres y las citas.

- Ofrece inicios **cada 15 min** desde el comienzo de cada tramo, con tres condiciones:
  - el servicio cabe en el tramo (el margen puede pasar de la hora de cierre);
  - no pisa ninguna cita hasta su `blocked_until`;
  - empieza al menos 30 min después de ahora.
- **Cambio de hora:**
  - Las horas que no existen (en marzo) no se ofrecen.
  - Las que se repiten (en octubre) se ofrecen una sola vez, con el desfase de verano.
  - Las duraciones son tiempo real.
  - Los límites del día en UTC se calculan en la zona del negocio, porque un día puede durar 23 o 25 h.
- Reglas fijas en `BookingPolicy`:

  | Regla | Valor |
  |---|---|
  | Paso entre inicios | 15 min |
  | Antelación mínima | 30 min |
  | Reservas hasta | 60 días vista |
  | Días por consulta | 14 como máximo |

- **Una sola fuente de verdad.** Al reservar, la API vuelve a calcular los huecos de ese día y exige que `startsAt` sea exactamente uno de ellos. Si no lo es (fuera de horario, fuera de la rejilla, en el pasado, ausencia…), responde 409.

## Reserva y concurrencia

1. `findSlot` valida el hueco y ordena los profesionales libres: primero el que tiene **menos citas ese día**; si empatan, por nombre.
2. Para cada profesional, en una **transacción propia**:
   1. bloquea su fila (`FOR NO KEY UPDATE`);
   2. busca o crea el cliente (`INSERT … ON CONFLICT DO NOTHING`);
   3. inserta la cita con `saveAndFlush`.
3. Si la BD responde con `23P01`, se prueba con el siguiente profesional. Si no queda ninguno, la API responde 409.

### Por qué una transacción por intento
En PostgreSQL, después de un error la transacción queda abortada: no se puede reintentar dentro de ella.

### Por qué la cola por profesional (lo descubrieron los tests)
Al comprobar un `EXCLUDE`, PostgreSQL primero inserta en el índice y después busca conflictos. Dos `INSERT` simultáneos ven cada uno la fila sin confirmar del otro y se esperan mutuamente: es un **deadlock**. PostgreSQL lo resuelve abortando a uno (`40P01`), pero solo lo detecta tras `deadlock_timeout` (1 s) y de uno en uno. Con 20 clientes, el último esperaba unos 19 s.

Con el bloqueo de la fila del profesional, los que compiten hacen cola: el segundo espera solo a que el primero confirme (milisegundos) y recibe un `23P01` limpio. **El bloqueo solo ordena; la garantía sigue siendo la restricción**, y un `40P01` se sigue tratando como «ocupado».

### Clientes
- Se buscan por email dentro de cada negocio.
- Los datos de un cliente que ya existe **no se sobrescriben**: el email no está verificado y no debe servir para cambiar los datos de otra persona.

## API pública (`/api/v1/public/businesses/{slug}`, sin sesión)

| Endpoint | Qué hace |
|---|---|
| `GET /` | Nombre y zona horaria; servicios activos que hace algún profesional activo; profesionales con sus servicios |
| `GET /availability?serviceId&staffId?&from&to` | Huecos día a día (incluye los días vacíos). `time` es la hora local (`"10:15"`) y `startsAt`, el instante exacto |
| `POST /appointments` | Reserva como invitado → 201 con el resumen. 409 si el hueco ya no está libre. CSRF obligatorio |

- **El tenant sale del slug:** `PublicTenantFilter` hace con la ruta lo mismo que `TenantFilter` hace con el JWT. Los servicios no saben si la petición viene del panel o de la página pública. Un slug que no existe responde 404 en el propio filtro.
- En las rutas públicas no se lee la cookie del JWT (`PUBLIC_PATHS`). La sesión del panel de otro negocio no cambia el tenant (hay un test).
- La web recibe la hora ya formateada en la zona del negocio, así que **no hace falta ninguna librería de zonas horarias** en el navegador.

## Web

- **`/[slug]`:**
  1. Servicio (radios nativos).
  2. Profesional (`<select>` con «Cualquier profesional»).
  3. Tira de 7 días (los días sin huecos salen desactivados) y horas agrupadas en mañana y tarde.
  4. Datos del cliente y confirmación.
- **Si la API responde 409**, se avisa, se recargan los huecos y el cliente vuelve al paso de la hora. El `useForm` vive en el componente padre, así que **lo que había escrito no se pierde**.
- Con `cacheComponents`, `params` se espera dentro de un `Suspense`. El build marca la ruta como prerender parcial.
- En `/panel`, la ficha muestra el enlace a la página pública con un botón para copiarlo.
- Las rutas fijas (`/panel`, `/login`…) tienen prioridad. El registro ya rechazaba esos slugs (`RESERVED_SLUGS`).

## Tests

| Test | Qué demuestra |
|---|---|
| `AvailabilityCalculatorTest` | Rejilla, jornada partida, margen, citas seguidas, cierres y ausencias, antelación, **cambio de hora de marzo y octubre**, Nueva York, sin pasar de la medianoche |
| `BookingSchemaTests` (SQL directo) | Solape parcial, margen, citas seguidas, cancelar libera, profesionales distintos, FKs entre negocios, `CHECK`s, borrado del negocio en cascada |
| `BookingConcurrencyTests` | **20 reservas simultáneas → 1 cita y 19 respuestas 409** · `INSERT` simultáneos saltándose la aplicación → solo 1 · «cualquiera» con 2 profesionales y 3 peticiones → 2 citas y 1 409 |
| `PublicBookingApiTests` | Página pública, huecos, rangos, inactivos → 404, solo huecos ofrecidos, validación, cliente sin sobrescribir, CSRF |
| `BookingIsolationTests` | Ids de B a través del slug de A → 404 y B intacto; cada página ve solo su negocio; mismo email = dos clientes |
| Web (Vitest + MSW) | Flujo completo, filtro de profesionales, semanas, días sin huecos, 409 que conserva los datos, validación, negocio inexistente o sin servicios |

También se ha probado en local con Tomcat real (no MockMvc): 10 `curl` simultáneos del mismo hueco → 1 × 201 y 9 × 409.

## Fuera de esta fase

- Email de confirmación y enlace para cancelar o cambiar la cita (fase 7).
- Agenda del dueño y del empleado (fase 8).
- Clientes con cuenta. El E2E con dos navegadores y el límite de peticiones van en la fase 10.
