# ADR 0006 · Trabajos periódicos desde un cron externo, sin `@Scheduled`

- **Estado:** aceptada (fase 1; implantada en las fases 7, 9 y 10)
- **Detalle:** [fase 1 § Alojamiento](../fase-1-analisis.md#-recomendación) · [fase 7 § El job](../fase-7-emails.md#el-job-internaljobsoutbox)

## Contexto

La API escala a cero en Cloud Run: sin peticiones no hay instancia, y un `@Scheduled` no se ejecutaría.

## Decisión

Los trabajos son endpoints internos (`/internal/jobs/{outbox,demo-cleanup,mail-heartbeat}`) que llama
**GitHub Actions** con la cabecera `X-Jobs-Token`:

- Cadena de Spring Security propia, sin cookies ni CSRF. El token se compara en tiempo constante; sin
  token configurado, los endpoints responden 401.
- No usan `Authorization: Bearer`: el resource server lo interpretaría como un JWT.
- Fuera del contrato OpenAPI (`@Hidden`).
- Cada hora y no más a menudo: cada llamada despierta Neon.

## Consecuencias

- Los trabajos son idempotentes y se pueden lanzar a mano (`workflow_dispatch` o `curl`).
- GitHub desactiva los cron tras 60 días sin actividad: `maintenance.yml` los reactiva cada semana.
- Cloud Run responde 411 a un `POST` sin cuerpo ni `Content-Length`: los workflows lo envían.
