# ADR 0002 · Multi-tenant con esquema compartido y `@TenantId`

- **Estado:** aceptada (fase 1; implantada en la fase 4)
- **Detalle:** [fase 1 § Aislamiento](../fase-1-analisis.md#aislamiento-de-los-datos-de-cada-negocio-multi-tenant) · [fase 4 § Multi-tenant](../fase-4-identidad.md#multi-tenant-tres-capas)

## Contexto

Muchos negocios comparten la aplicación y ninguno puede ver nada de otro. La BD es un PostgreSQL gratuito
(Neon): una base de datos por negocio no es posible y un esquema por negocio complica Flyway.

## Decisión

Una BD y un esquema compartidos, con `business_id` en cada tabla del negocio, protegido en **tres capas**:

1. **Aplicación:** `@TenantId` de Hibernate. El tenant sale del JWT (panel) o del slug (páginas públicas) y
   se fija al abrir la sesión de Hibernate, que añade `business_id = ?` a todas las consultas. Sin tenant,
   un id que no existe: *fail closed*, nunca «todos los datos».
2. **Base de datos:** FKs **compuestas** `(business_id, id)`. Por esquema es imposible que una cita del
   negocio A apunte a un profesional del B.
3. **Tests:** cada endpoint tiene su test de aislamiento (A pide algo de B → **404**, nunca 403, para no
   confirmar que existe).

Los endpoints nunca reciben un `businessId` del cliente. Lo que cruza negocios (login, registro) va por
`SystemTransactions`, que se niega a ejecutarse dentro de otra transacción.

## Alternativas descartadas

- **Esquema por negocio:** migraciones N veces y conexiones por esquema, sin ventaja con pocos negocios.
- **BD por negocio:** imposible en planes gratuitos.
- **Solo filtros a mano (`WHERE business_id = ?`):** basta un olvido para filtrar datos de otro negocio.

## Consecuencias

- Un olvido en el código no basta para filtrar datos: lo frenan Hibernate y las FKs, y lo detectan los tests.
- Mejora posible: **Row Level Security** de PostgreSQL como cuarta capa (no implantada).
