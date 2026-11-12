# Render (plan B de la API)

La misma imagen Docker de la API, desplegada en Render (Free, Frankfurt) contra la misma base de datos de Neon.
Se probó el 2026-11-12 (`/actuator/health` → `UP`) y se dejó **suspendido**.

- Blueprint: [render.yaml](render.yaml) (en Render: *New → Blueprint*, Blueprint Path `infra/render/render.yaml`).
- `DB_URL`, `DB_USER` y `DB_PASSWORD` se introducen en el panel de Render; no están en el repo.
- No se despliega solo (`autoDeploy: false`).

## Si Cloud Run falla

1. Render → `slotea-api-planb` → **Resume**. Si el código ha cambiado desde el último despliegue: **Manual Deploy → Deploy latest commit**.
2. Comprueba `https://slotea-api-planb.onrender.com/actuator/health` → `UP`. El plan Free se duerme sin tráfico y la primera petición tarda.
3. Vercel → proyecto `slotea` → *Settings → Environment Variables* → `API_URL` = `https://slotea-api-planb.onrender.com`.
4. Vercel → *Deployments* → **Redeploy**: los `rewrites` se calculan al construir.
5. Cuando Cloud Run vuelva: deshaz los pasos 3–4 y suspende el servicio de Render.
