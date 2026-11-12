# Fase 5 — Catálogo del negocio

Lo que un dueño configura antes de poder recibir reservas: **servicios**, **profesionales** (y qué servicios hace cada uno), su **horario semanal** y los **cierres** (festivos del negocio y ausencias del equipo).

## Modelo de datos (migración `V3`)

```
staff_member   profesional; user_id opcional → enlace con un miembro del MISMO negocio
service        nombre único por negocio (sin distinguir mayúsculas), duración y margen de 5 en 5 min, precio opcional (€)
staff_service  qué servicios hace cada profesional             PK (business_id, staff_id, service_id)
working_hours  tramos semanales en hora local (1 = lunes … 7 = domingo); varios por día = jornada partida
closure        días sin servicio (fechas inclusivas); staff_id NULL = todo el negocio
```

Todas siguen el patrón de la fase 4: `business_id` + `UNIQUE (business_id, id)` y **FKs compuestas** `(business_id, x_id)`. Por esquema, un horario, una ausencia o una asignación no pueden apuntar a un profesional o servicio de otro negocio.

### Decisiones

- **Hora local, no UTC.** Los horarios (`time`) y los cierres (`date`) se guardan en la hora local del negocio, porque «abre a las 9:00» sigue siendo a las 9:00 en invierno y en verano. La conversión a `timestamptz` y los tests del cambio de hora llegan en la fase 6, al calcular los huecos.
- **La BD impide los tramos solapados.** Es el mismo patrón que impedirá la doble reserva: un tipo de rango propio (`CREATE TYPE timerange AS RANGE (subtype = time)`) y una restricción `EXCLUDE USING gist`. Con `'[)'`, 10:00–14:00 y 14:00–18:00 son contiguos, no solapados. La aplicación valida antes (`WeeklySchedule`) para devolver un 400 que señala el tramo exacto (`slots[1].start`).
- **Desactivar, no borrar.** Servicios y profesionales tienen `active`: las citas de la fase 6 los referencian y el historial no se puede romper. Los cierres y los tramos sí se borran.
- **Cierres por días completos.** Fechas inclusivas (`start_date`, `end_date`). En las consultas, `daterange(start_date, end_date, '[]')`. En lugar de una columna `daterange` usamos dos `date` para que Hibernate las mapee sin librerías extra (`ddl-auto=validate`). Las ausencias de unas horas quedan fuera de la v1.
- **Enlace profesional ↔ usuario.** La FK `(business_id, user_id) → membership` exige que el usuario sea miembro del negocio. Si pierde la membresía, `ON DELETE SET NULL (user_id)` desenlaza la ficha, pero la conserva (PostgreSQL 15+).
- **`staff_service` con SQL, no con `@ManyToMany`.** `@TenantId` no se aplica a las tablas puente. `StaffServiceLinks` la maneja con `JdbcClient`, y el `business_id` sale siempre de `TenantContext`, nunca de la petición.
- **Horario semanal sin «horario del negocio».** Cada profesional tiene el suyo. El horario del negocio es la suma de los de su equipo, así que no hay dos fuentes de verdad.

## API (`/api/v1`)

| Endpoint | Rol |
|---|---|
| `GET /services` · `GET /services/{id}` | STAFF |
| `POST /services` · `PUT /services/{id}` | OWNER |
| `GET /staff` · `GET /staff/{id}` (incluye `serviceIds`) | STAFF |
| `POST /staff` · `PUT /staff/{id}` · `PUT /staff/{id}/services` | OWNER |
| `GET /staff/{id}/working-hours` · `PUT …` (semana completa) | STAFF · OWNER |
| `GET /closures?staffId=&from=&to=` | STAFF |
| `POST /closures` · `DELETE /closures/{id}` | OWNER |

- **`PUT` y no `PATCH`** para servicios y profesionales: el formulario envía el recurso completo, y así `null` significa «sin precio» y no «no lo cambies».
- Las horas viajan como `"HH:mm"` (`@JsonFormat`), igual que las usa `<input type="time">`.
- Un id de otro negocio, en la ruta **o en el cuerpo** (`serviceIds`, `staffId`, `userId`), da **404**.

### Trampa: Hibernate 7 y las restricciones `EXCLUDE`

Hibernate traduce las violaciones de `UNIQUE`/`FK`/`CHECK` a `ConstraintViolationException` con el nombre de la restricción, pero **las de `EXCLUDE` (SQLState `23P01`) no**: llegan como un error JDBC genérico. `ApiExceptionHandler` lee el nombre directamente de la `PSQLException` del driver (por eso el driver pasa a scope `compile`) y lo traduce a 409. `ConstraintTranslationTests` lo comprueba con PostgreSQL real. La fase 6 necesita esto para la doble reserva.

## Panel

| Ruta | Contenido |
|---|---|
| `/panel` | Saludo + «Configura tu negocio» (lista de pasos) y «Añádete como profesional» en un clic |
| `/panel/servicios` | Tabla; crear y editar en un diálogo |
| `/panel/profesionales` | Lista con los servicios de cada uno; crear en un diálogo |
| `/panel/profesionales/[id]` | Pestañas: Datos · Servicios · Horario (editor semanal, «copiar el lunes de martes a viernes») · Ausencias |
| `/panel/cierres` | Cierres de todo el negocio y ausencias del equipo |

- `PanelShell` (layout de `/panel`) comprueba la sesión una sola vez y pinta la navegación.
- El empleado ve todo **en solo lectura**. La interfaz oculta los controles y, además, la API responde 403.
- Fechas y horas con `<input type="date|time">` nativos y desplegables `<select>` nativos: son accesibles y no añaden dependencias.
- La validación en el navegador (`lib/validation/catalog.ts`) replica las reglas de la API, incluidos los solapes de tramos, para avisar antes de enviar.

### Trampa: `usePathname` y `cacheComponents`

Con `cacheComponents`, un componente cliente que llama a `usePathname()` bloquea el prerender de las rutas con parámetros (`/panel/profesionales/[id]`). La navegación va en un `Suspense` cuyo *fallback* es la misma navegación sin marcar la página actual.

## Tests

- **`CatalogIsolationTests`**: una **tabla con todos los endpoints que reciben un id**, ejecutada como test parametrizado. El dueño de A usa ids de B (en la ruta o en el cuerpo) → 404, y después se comprueba que B no ha cambiado. Incluye las listas y los permisos de STAFF (lee sí, escribe 403). **Cada endpoint nuevo añade su fila.**
- **`CatalogSchemaTests`**: SQL directo, sin pasar por la aplicación. Las FKs compuestas impiden mezclar negocios, el `EXCLUDE` rechaza tramos solapados y permite los contiguos, y los `CHECK` rechazan duración 7, día 8, fechas al revés o precio negativo.
- **`CatalogApiTests`**: los flujos completos (PUT que vacía campos, 409 por nombre duplicado, enlace del dueño como profesional, jornada partida, sustitución del horario, filtros de cierres).
- **`WeeklyScheduleTest`**: reglas del horario (solapes, orden, rejilla de 5 min).
- **Web (Vitest + MSW)**: validaciones, crear y editar servicios (incluido el mapeo `priceCents` → campo precio), editor de horario (jornada partida, copiar días, solape sin llamar a la API), cierres, ficha del profesional y vistas de solo lectura del empleado.

## Fuera de esta fase

- Cálculo de huecos y reservas (fase 6).
- Ausencias de unas horas, horarios especiales para un día concreto.
- Que el empleado gestione sus propias ausencias (fase 8, con su agenda).
