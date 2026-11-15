# ADR 0003 · La doble reserva la impide la base de datos

- **Estado:** aceptada (fase 1; implantada en las fases 5 y 6)
- **Detalle:** [fase 1 § Doble reserva](../fase-1-analisis.md#cómo-se-evita-la-doble-reserva) · [fase 6 § Reserva y concurrencia](../fase-6-reservas.md#reserva-y-concurrencia)

## Contexto

Dos clientes pueden pulsar la misma hora a la vez, en instancias distintas de la API. Comprobar «¿está
libre?» y después insertar deja una ventana en la que los dos ven el hueco libre.

## Decisión

Una restricción **`EXCLUDE USING gist`** en `appointment`: el mismo profesional no puede tener dos citas
no canceladas cuyos rangos `[inicio, fin + margen)` se solapen. PostgreSQL la resuelve de forma atómica;
la API traduce el `23P01` a **409**.

Además, `BookingService`:
- **bloquea la fila del profesional** (`FOR NO KEY UPDATE`) antes de insertar, para que los que compiten
  hagan cola en vez de bloquearse entre sí (lo descubrieron los tests: sin ello, `EXCLUDE` + inserciones
  simultáneas = deadlocks detectados de uno en uno, tras 1 s cada uno);
- abre **una transacción por intento**: tras un error, PostgreSQL aborta la transacción y no se puede
  reintentar dentro de ella. Con «me da igual el profesional», si uno da 409 se prueba con el siguiente.

## Alternativas descartadas

- **Comprobar en Java y después insertar:** condición de carrera.
- **Bloqueo optimista o un `UNIQUE (staff, starts_at)`:** no detecta solapamientos parciales
  (10:00–10:30 contra 10:15–10:45).
- **Bloqueo distribuido (Redis):** otra pieza que mantener, y la garantía seguiría fuera de los datos.

## Consecuencias

- La regla crítica no depende del código: aunque falle una validación, la BD no admite el solapamiento.
- Test con 20 hilos sobre PostgreSQL real (Testcontainers): exactamente 1 éxito y 19 respuestas 409.
- Hibernate 7 no da nombre a las violaciones de `EXCLUDE`: `ApiExceptionHandler` lo lee de la `PSQLException`.
