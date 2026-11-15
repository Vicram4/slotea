# ADR 0007 · Demo: una copia del negocio por visitante

- **Estado:** aceptada (fase 9; plantillas por sector en la fase 10)
- **Detalle:** [fase 9](../fase-9-demo.md)

## Contexto

Quien evalúa el proyecto tiene que poder probarlo en segundos, sin registrarse, como negocio, como
empleado o como cliente, y sin que lo que hagan otros le estropee la demo.

## Decisión

Cada clic en la portada crea **una copia propia** de un negocio ficticio (`is_demo`, slug `demo-*`) a
partir de una plantilla del sector elegido (seis), y abre sesión en ella. La copia se borra a las 24 h
(job `demo-cleanup`).

- Los emails de una copia nunca salen: se guardan con estado `DEMO` y se ven en la **bandeja de la demo**.
- Los slugs `demo-*` están reservados; cambiar de rol solo funciona dentro de una copia.
- Límites: 20 copias por hora y por IP, 300 por hora en total y 100 vivas a la vez.

## Alternativas descartadas

- **Una demo compartida con reset diario:** cada visitante ve lo que dejaron los demás, y basta uno para
  estropearla hasta el reset.
- **Cuentas de prueba fijas:** el mismo problema, y además credenciales públicas.

## Consecuencias

- Cada visitante ve siempre una agenda limpia y coherente, con fechas relativas a hoy.
- Coste: unas decenas de filas por copia, y el job de limpieza.
