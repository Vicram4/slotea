# Decisiones de arquitectura (ADR)

Cada ADR recoge una decisión con su contexto, las alternativas y lo que implica. El razonamiento largo
está en los documentos de cada fase, enlazados desde cada ADR.

| # | Decisión |
|---|---|
| [0001](0001-csp-estatica-sin-nonce.md) | CSP estática, sin nonce |
| [0002](0002-multi-tenant-esquema-compartido.md) | Multi-tenant con esquema compartido y `@TenantId` |
| [0003](0003-doble-reserva-en-la-base-de-datos.md) | La doble reserva la impide la base de datos |
| [0004](0004-sesion-jwt-en-cookie.md) | Sesión con JWT en cookie `httpOnly` y la API detrás de Vercel |
| [0005](0005-emails-con-outbox.md) | Emails con una outbox transaccional |
| [0006](0006-jobs-desde-un-cron-externo.md) | Trabajos periódicos desde un cron externo, sin `@Scheduled` |
| [0007](0007-demo-una-copia-por-visitante.md) | Demo: una copia del negocio por visitante |
| [0008](0008-limites-de-peticiones-en-memoria.md) | Límites de peticiones en memoria, por IP y globales |
| [0009](0009-alojamiento-gratuito-portable.md) | Alojamiento gratuito y portable |
