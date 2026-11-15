# ADR 0009 · Alojamiento gratuito y portable

- **Estado:** aceptada (fase 1; desplegada en la fase 3)
- **Detalle:** [fase 1 § Alojamiento gratuito](../fase-1-analisis.md#2-alojamiento-gratuito)

## Contexto

El proyecto tiene que estar en línea sin coste y sin vigilancia constante. Una experiencia anterior con
otro proveedor gratuito falló porque todo dependía de un único servicio y nadie se enteraba de las caídas.

## Decisión

| Pieza | Principal | Plan B |
|---|---|---|
| API | **Google Cloud Run** (europe-west1, escala a cero, `max-instances=2`) | Render, con la misma imagen (probado una vez) |
| Base de datos | **Neon** (PostgreSQL, Frankfurt) | Supabase |
| Web | **Vercel** | Netlify |
| Email | **Brevo SMTP** | Gmail SMTP |

Tres reglas: la API es una **imagen Docker portable** (cambiar de proveedor es cambiar un archivo de
despliegue), la **BD va aparte** del servidor, y hay **vigilancia** (prueba de humo diaria en GitHub
Actions y latido semanal del email). Los datos de la demo se generan desde el código.

## Alternativas descartadas

- **Render como principal:** arranque en frío de más de un minuto con Spring y condiciones cambiantes en 2026.
- **Una VM gratuita:** mantenimiento del sistema a cargo nuestro y riesgo de que la reclamen.

## Consecuencias

- Arranque en frío de unos segundos: la portada despierta la API con un ping mientras el visitante elige
  sector, y el lanzador avisa de «Despertando el servidor…» si tarda.
- No hay `@Scheduled` (ADR 0006).
- Cloud Run pide tarjeta: el riesgo se acota con `max-instances` y una alerta de presupuesto.
