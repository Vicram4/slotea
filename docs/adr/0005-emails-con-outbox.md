# ADR 0005 · Emails con una outbox transaccional

- **Estado:** aceptada (fase 7)
- **Detalle:** [fase 7 § Outbox](../fase-7-emails.md#outbox-ningún-email-perdido-ninguno-de-más)

## Contexto

Cada reserva, cambio o cancelación avisa al cliente y al negocio. Enviar el email dentro de la
transacción arriesga mandar un email de una reserva que luego hace rollback. Enviarlo después sin más
arriesga perderlo si el SMTP falla.

## Decisión

- El email se **encola en `email_outbox` en la misma transacción** que el cambio: o las dos cosas, o ninguna.
- Se envía **justo después del commit, en la misma petición** (`EmailOutbox.sendNow`). No en segundo
  plano: Cloud Run quita la CPU al responder. Tampoco en `AFTER_COMMIT`: la transacción aún tiene su
  conexión y el pool es de 5.
- Lo que falle lo reintenta un **job cada hora** (ver ADR 0006), con reclamación `FOR UPDATE SKIP LOCKED`
  y un *lease* de 2 min, y esperas crecientes entre reintentos. Tras 5 intentos queda `FAILED`.
- En la demo, el `INSERT` guarda el email con estado `DEMO` y no se envía nunca; se ve en la bandeja de la demo.

## Consecuencias

- Entrega **al menos una vez**: si la instancia muere entre el envío y marcarlo `SENT`, puede salir
  duplicado. La alternativa (como mucho una vez) perdería emails.
- El health de la API no depende del SMTP: un Brevo lento no tumba la API.
