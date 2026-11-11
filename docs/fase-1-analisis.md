# Fase 1 — Análisis y alcance

> Fecha de la investigación: 11-11-2026. Estado: **aprobado el 11-11-2026**.
>
> **Decisiones finales:** nombre **Slotea** · API en **Cloud Run** (plan B: Render) · **se permiten reservas de invitado**.
> Las cifras de planes gratuitos cambian a menudo: se vuelven a comprobar el día del despliegue.

---

## 1. Nombre

### ¿Está libre «Turnio»?

| Comprobación | Resultado |
|---|---|
| Marca conocida | **No está libre en la práctica.** *Turn.io* es una empresa (EE. UU./Sudáfrica) de mensajería por WhatsApp y la prensa la escribe «Turnio». Es otro sector, pero también es software. |
| `turnio.com` | Está en uso |
| `turnio.vercel.app` | Ocupado |
| `github.com/turnio` | Ocupado (no importa: el repo irá en tu cuenta, `github.com/<tu-usuario>/<nombre>`) |
| npm `turnio` | Libre (no lo vamos a usar) |

Para una demo de portfolio el riesgo legal es bajo, pero hay dos problemas reales: la URL de Vercel tendría que ser algo como `turnio-app.vercel.app`, y quien busque el nombre en Google encontrará otra empresa.

### Alternativas comprobadas (nombre libre en GitHub y en Vercel)

| Nombre | Español | Inglés | `.vercel.app` | `.com` |
|---|---|---|---|---|
| **Slotea** ⭐ | «slotear» (coger un hueco) suena natural | «slot» es la palabra exacta | libre | ocupado |
| Turnely | «turno» | «turn» + -ly, estilo SaaS | libre | ocupado |
| Turnoly | «turno» | se entiende peor | libre | ocupado |
| Huecora | «hueco» | no funciona en inglés | libre | ocupado |

**Recomendación: Slotea.** Se entiende en los dos idiomas, describe lo que hace la app (reservar un *slot*) y la URL `slotea.vercel.app` está libre. No dependemos del `.com`, porque no vamos a pagar dominio.

**Lo que tienes que hacer tú (5 min)** antes de decidir:
1. Entra en https://www.tmdn.org/tmview/ (marcas de la UE y de España), escribe el nombre y mira las clases 9 y 42 (software).
2. Busca el nombre entre comillas en Google.
3. Si todo está limpio, crea ya el proyecto en Vercel con ese nombre para reservar el subdominio.

---

## 2. Alojamiento gratuito

### Lo que aprendimos de Northflank
Lo que falló no fue solo el proveedor, también la forma de montarlo: **todo dependía de un único servicio gratuito y nadie se enteraba cuando se caía**. El plan tiene tres reglas:
1. **Imagen Docker portable.** La API no depende de nada propio de un proveedor. Cambiar de proveedor es cambiar un archivo de despliegue.
2. **La base de datos va separada del servidor.** Si se cae el alojamiento de la API, los datos siguen en su sitio.
3. **Vigilancia.** Un workflow de GitHub Actions comprueba cada día que la demo responde (prueba de humo) y te avisa por email si falla. Te enteras tú antes que un reclutador.
4. Extra: los datos de la demo **se generan desde el código** (seed). Si perdemos la base de datos, se recrea en un minuto.

### API (Spring Boot en Docker)

| Opción | ¿Se duerme? | Arranque en frío | Límites | Tarjeta | Riesgo de que cambien las condiciones |
|---|---|---|---|---|---|
| **Google Cloud Run** | Sí, baja a cero instancias | **Rápido** (~5–10 s con Spring y CDS/AOT) | 2 M peticiones/mes, 180 000 vCPU-s, 360 000 GiB-s | **Sí** | **Bajo**: el plan gratuito existe desde 2019 y es de Google |
| Render (free) | Sí, a los 15 min | **Lento** (~1 min según la documentación, más con Spring) | 512 MB, 0,1 vCPU, 750 h/mes; tráfico reducido en 2026 | No | **Medio-alto**: en 2026 cambió de planes (1 de agosto) y redujo el tráfico gratuito unas 20 veces |
| Koyeb | Sí (a la hora) | ~1–5 s | 512 MB, 0,1 vCPU | Sí (retención de 29 $) desde 2026 | **Alto**: se integra en Mistral AI y tiene las altas restringidas |
| Fly.io | — | — | Sin plan gratuito para cuentas nuevas | — | Ya no es opción |
| Oracle Always Free (VM) | No | — | Te reclaman la máquina si va por debajo del 20 % de CPU (una API en reposo cumple eso) | Sí | **Alto**: hay noticias de recorte en 2026 y bloqueos de cuentas; además el mantenimiento del servidor es cosa tuya |

### Base de datos (PostgreSQL)

| Opción | ¿Se duerme? | Límites | Riesgo |
|---|---|---|---|
| **Neon** ⭐ | El cómputo baja a cero a los 5 min y despierta en menos de 1 s. **El proyecto no se pausa** | 100 CU-h/mes por proyecto, ~0,5 GB por proyecto | Bajo/medio. Soporta `btree_gist`, que necesitamos para impedir dobles reservas (lo dice su documentación) |
| Supabase | **Pausa el proyecto tras 7 días sin actividad** | 500 MB, 2 proyectos | Medio. Sirve como plan B si un ping semanal lo mantiene activo |
| Render Postgres free | — | **Caduca a los 30 días** | Descartada |

### Frontend
**Vercel Hobby**: gratis para proyectos no comerciales (un portfolio lo es), no se duerme y es el alojamiento natural de Next.js.

### Email
Vamos a mandar pocos correos (es una demo).
- **Brevo SMTP**: 300 al día, gratis y sin caducidad.
- Plan B: **SMTP de una cuenta de Gmail creada para el proyecto** (contraseña de aplicación).
- En local: **Mailpit** (bandeja falsa en Docker).
- Problema: sin dominio propio, los correos pueden acabar en spam. Lo probaremos de verdad al desplegar.
- Para la demo pública añadimos una **«bandeja de la demo»** dentro de la app, donde se ve el correo que se habría enviado. Así el reclutador lo ve sin dar su email.
- Resend lo descartamos: sin dominio verificado solo envía a tu propia dirección.

### ✅ Recomendación

| Pieza | Principal | Plan B (preparado y probado una vez) |
|---|---|---|
| API | **Google Cloud Run** (región europe-west1, `max-instances=1`, alerta de presupuesto de 1 €) | **Render free**, con la misma imagen y `render.yaml` en el repo |
| Base de datos | **Neon** (Frankfurt) | **Supabase**. Como el seed se genera desde el código, migrar es ejecutar Flyway y el seed |
| Frontend | **Vercel** | Netlify |
| Email | **Brevo SMTP** | Gmail SMTP |
| Vigilancia | GitHub Actions: prueba de humo diaria + UptimeRobot gratis | — |

**Por qué Cloud Run y no Render, aunque pida tarjeta:** Render es lo más cómodo, pero un arranque en frío de más de un minuto con Spring hace que un reclutador cierre la pestaña. Además, Render ha cambiado sus condiciones dos veces en 2026. El plan gratuito de Cloud Run es el más estable del mercado. El riesgo de que te cobren se controla con `max-instances=1` y una alerta de presupuesto. **Si prefieres no poner tarjeta**, invertimos el orden: Render como principal y Cloud Run como plan B. La arquitectura es la misma.

**Consecuencia para el diseño:** como la API «se duerme», **no podemos usar `@Scheduled`** para los recordatorios: si no hay instancia encendida, la tarea no se ejecuta. Las tareas programadas se lanzan desde fuera (GitHub Actions o Cloud Scheduler, que tiene 3 trabajos gratis) contra un endpoint interno protegido con un token secreto. La pantalla de la demo también muestra un «Despertando servidor…» amable mientras arranca.

---

## 3. Versiones y librerías

Criterio: la última versión **estable con parches** (nada de milestones ni RC) y con soporte LTS cuando lo haya.

### Backend
| Pieza | Versión | Notas |
|---|---|---|
| Java | **25 LTS** (Temurin) | Spring Boot 4 pide como mínimo 17. Usamos la LTS más reciente (lo recomienda la guía oficial) y su caché AOT/CDS para arrancar más rápido |
| Spring Boot | **4.1.x** (último parche al crear el proyecto; hoy 4.1.2) | 4.2 está en RC y saldrá en noviembre de 2026: actualizaremos cuando tenga su primer parche. 4.0 pierde soporte gratuito el 31-12-2026 |
| Spring Framework / Security | 7.0 / 7.1 (vienen con Boot) | Spring Security 7 solo admite la configuración con lambdas. Viene con soporte para CSRF en SPA (`csrf.spa()`) |
| Hibernate | 7.4 (con Boot) | Tiene `@TenantId` para multi-tenant por columna |
| Flyway | 12.x (con Boot) | ⚠️ En Boot 4 hace falta **`spring-boot-starter-flyway`**; ya no basta con `flyway-core` |
| Build | **Maven** + wrapper | Es lo más habitual en consultoras y banca en España |
| API docs | **springdoc-openapi 3.0.x** | La rama 3 es la que funciona con Boot 4 |
| Tests | JUnit (la versión del BOM de Boot), **Testcontainers 2.0.x** | ⚠️ Testcontainers 2 cambia los nombres: `testcontainers-postgresql`, `testcontainers-junit-jupiter`, paquete `org.testcontainers.postgresql` |
| Email | `spring-boot-starter-mail` + Thymeleaf para las plantillas | Funciona con cualquier SMTP |
| Otros | Bean Validation, Actuator (health), Bucket4j para limitar peticiones (fase de calidad) | Sin Lombok: usamos `record` |

**Cambios de Spring Boot 4 que hay que tener presentes** (guía oficial de migración):
- Los starters son modulares: `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server` y uno de test por tecnología (`spring-boot-starter-webmvc-test`, `-data-jpa-test`…).
- **Jackson 3**: los paquetes pasan a `tools.jackson.*`, salvo las anotaciones, que siguen en `com.fasterxml.jackson.annotation`.
- Se eliminan `@MockBean` y `@SpyBean`; ahora se usan `@MockitoBean` y `@MockitoSpyBean`.
- `@SpringBootTest` ya no configura MockMvc ni TestRestTemplate: hay que añadir `@AutoConfigureMockMvc` o `@AutoConfigureRestTestClient`.
- JSpecify `@Nullable`. RestTemplate queda obsoleto en 4.2: usaremos `RestClient`.

### Base de datos
**PostgreSQL 17**, la misma versión en local (Docker), en Neon y en el plan B (Supabase), para que todo sea compatible.

### Frontend
| Pieza | Versión | Notas |
|---|---|---|
| Node.js | **26 LTS** (LTS desde octubre de 2026, versión por defecto en Vercel) | Plan B: 24 LTS |
| Next.js | **16.4.x** (≥ 16.4.5 por el parche de seguridad de noviembre) | App Router. Turbopack por defecto. ⚠️ Acceso **asíncrono** obligatorio a `params`, `cookies()` y `headers()`. `next lint` desaparece: se usa ESLint directamente |
| React | 19.x (la que traiga Next) | |
| TypeScript | la estable que instale `create-next-app` (≥ 5.1 obligatorio) | `strict` activado |
| Estilos / UI | Tailwind CSS 4 + shadcn/ui | Accesible y sin depender de un kit de pago |
| Datos | **TanStack Query 5** en el panel. En las páginas públicas, Server Components | |
| Cliente API | **openapi-typescript + openapi-fetch** | Los tipos se generan desde el OpenAPI de Spring: un solo contrato y tipos de punta a punta |
| Formularios | react-hook-form + Zod 4 | |
| Fechas | date-fns 4 + `@date-fns/tz` | Hay que manejar el cambio de hora de Europe/Madrid |
| i18n (extra) | next-intl | |
| Tests | Vitest + Testing Library + MSW; **Playwright** para E2E | |
| Gestor de paquetes | **pnpm** | |

### Autenticación — recomendación
**Spring Security con JWT propio, guardado en una cookie `httpOnly`**, sin proveedores externos (Auth0, Clerk…):
- La API firma el JWT con `NimbusJwtEncoder` (incluido en el resource server; no hace falta `jjwt`). El token de acceso dura 15 min y el *refresh token* se guarda **hasheado en la base de datos** y rota en cada uso (permite cerrar sesión de verdad).
- El navegador **nunca ve el token** (cookie `httpOnly`, `Secure`, `SameSite=Lax`) y CSRF queda protegido con `csrf.spa()`.
- **Truco clave:** Next.js reenvía `/api/*` a la API con `rewrites` de Vercel. Para el navegador todo vive en el mismo dominio, así que las cookies son *first-party* y no hay CORS que configurar. Esto evita el bloqueo de cookies de terceros entre `*.vercel.app` y `*.run.app`.
- Contraseñas con BCrypt (`DelegatingPasswordEncoder`).

¿Por qué no sesiones de servidor? Funcionarían igual de bien, pero JWT + refresh es lo que más se pregunta en entrevistas de consultoras y banca, y con esta implementación no tiene los problemas de seguridad habituales (token en localStorage).

---

## 4. Arquitectura

### Monorepo — recomendación: **sí, un solo repo**
Un reclutador ve todo de un vistazo, un PR puede cambiar a la vez el contrato y el frontend, y CI ejecuta solo lo que cambia (`paths` en los workflows). No hace falta Nx ni Turborepo: dos apps de lenguajes distintos se gestionan bien con un Makefile o con scripts.

```
slotea/                       (o el nombre elegido)
├── apps/
│   ├── api/                  Spring Boot (Maven)
│   │   ├── src/main/java/<paquete>/
│   │   │   ├── shared/       config, seguridad, errores (ProblemDetail), tenancy
│   │   │   ├── auth/         login, refresh, usuarios
│   │   │   ├── business/     negocio, miembros, profesionales
│   │   │   ├── catalog/      servicios
│   │   │   ├── schedule/     horarios, cierres, cálculo de disponibilidad
│   │   │   ├── booking/      citas, cancelar y cambiar con token
│   │   │   ├── notification/ outbox y emails
│   │   │   └── demo/         seed y acceso en un clic
│   │   ├── src/main/resources/db/migration/   V1__....sql (Flyway)
│   │   └── Dockerfile
│   └── web/                  Next.js
│       ├── src/app/
│       │   ├── (public)/[slug]/        página de reservas de cada negocio
│       │   ├── (public)/cita/[token]/  cancelar / cambiar
│       │   ├── (panel)/panel/...       agenda, servicios, horarios…
│       │   └── (auth)/login
│       ├── src/lib/api/      cliente generado desde OpenAPI
│       └── e2e/              Playwright
├── docs/                     decisiones (ADR), diagramas, este análisis
├── compose.yaml              Postgres + Mailpit en local (en la raíz para que baste `docker compose up`)
├── infra/                    render.yaml, cloudrun
├── .github/workflows/        api.yml, web.yml, e2e.yml, smoke-prod.yml, jobs.yml
├── CLAUDE.md
└── README.md
```

**Organización por funcionalidad** (*package by feature*), no por capas técnicas. Cada módulo tiene su `Controller → Service → Repository` y sus DTOs (`record`). No es arquitectura hexagonal completa: para este tamaño sería ceremonia sin beneficio, y en una entrevista se defiende bien la decisión.

### Comunicación
```
Navegador ──► Vercel (Next.js)
               ├── Server Components ──fetch──► API (reciben la cookie y la reenvían)
               └── /api/* (rewrite) ───────────► API Spring en Cloud Run ──► Neon
                                                  └── SMTP Brevo
GitHub Actions (cron) ──token──► /internal/jobs/* (recordatorios, reset de la demo)
```
- REST + JSON. Errores en formato **RFC 9457 `ProblemDetail`** (lo trae Spring).
- Versionado `/api/v1`.
- El contrato es el OpenAPI que genera springdoc. En CI se **regeneran los tipos de TS y falla si no coinciden** (así el front y la API no se desincronizan).

### Aislamiento de los datos de cada negocio (multi-tenant)
**Recomendación: una base de datos y un esquema compartidos, con columna `business_id` en cada tabla del negocio**, protegida en tres capas:
1. **Aplicación:** `@TenantId` de Hibernate en las entidades. Hibernate añade `business_id = ?` a todas las consultas, y el tenant actual sale del JWT (panel) o del `slug` (páginas públicas).
2. **Base de datos:** claves foráneas **compuestas** `(business_id, id)`. Es imposible, por esquema, que una cita del negocio A apunte a un profesional del negocio B.
3. **Tests:** una batería que comprueba que un usuario del negocio A recibe 404 en cada endpoint con recursos del negocio B.
4. (Opcional, en la fase de calidad) **Row Level Security** de PostgreSQL como cuarta capa. Da mucho juego para presumir en entrevistas de banca.

Descartados: un esquema por negocio (complica Flyway y no aporta nada con pocos negocios) y una base de datos por negocio (imposible en planes gratuitos).

---

## 5. Modelo de datos inicial

```
business        id, slug (único), name, timezone ('Europe/Madrid'), contact_email, is_demo, created_at
app_user        id, email (único, sin distinguir mayúsculas), password_hash, full_name, created_at
membership      business_id, user_id, role ('OWNER' | 'STAFF')         PK(business_id, user_id)
staff_member    id, business_id, user_id NULL, display_name, active     UNIQUE(business_id, id)
service         id, business_id, name, description, duration_min, buffer_min, price_cents NULL*, active
staff_service   business_id, staff_id, service_id                       (FK compuestas)
working_hours   id, business_id, staff_id, day_of_week (1-7), start_time, end_time   (varios tramos = jornada partida)
closure         id, business_id, staff_id NULL, period daterange, reason   (NULL = cierra todo el negocio; con valor = ausencia del profesional)
customer        id, business_id, user_id NULL, full_name, email, phone NULL     UNIQUE(business_id, email)
appointment     id, business_id, staff_id, service_id, customer_id,
                starts_at timestamptz, ends_at timestamptz, blocked_until timestamptz (fin + buffer),
                status ('CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'NO_SHOW'),
                manage_token_hash, notes, created_at, cancelled_at, version
email_outbox    id, business_id, type, to_email, payload jsonb, status, attempts, next_attempt_at, sent_at
refresh_token   id, user_id, token_hash, expires_at, revoked_at
```
\* `price_cents` solo se muestra; no hay pagos.

- **IDs:** UUID v7 generados en la aplicación (ordenables y no adivinables en las URLs públicas).
- **Horas:** siempre `timestamptz` en UTC. Los huecos se calculan en la zona horaria del negocio con `java.time`, con tests de los días de cambio de hora.
- **Roles:** dueño y empleado van en `membership` (por negocio). El **cliente** es un `app_user` sin membresía, con una ficha `customer` en cada negocio donde reserva. Además se puede **reservar como invitado** (solo con el email): el enlace del email (token aleatorio que guardamos *hasheado*) permite cancelar o cambiar sin cuenta. → **Decidido: sí.**

### Cómo se evita la doble reserva

**La garantía está en la base de datos**, no en el código Java:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointment ADD CONSTRAINT appointment_no_overlap
  EXCLUDE USING gist (
    staff_id WITH =,
    tstzrange(starts_at, blocked_until, '[)') WITH &&
  ) WHERE (status <> 'CANCELLED');
```

- Impide no solo «la misma hora» sino **cualquier solapamiento** (10:00–10:30 contra 10:15–10:45) del mismo profesional.
- `'[)'` deja reservar 10:30 justo cuando acaba la cita de las 10:00.
- Las citas canceladas liberan el hueco al instante.
- Funciona aunque dos peticiones lleguen a la vez, en procesos o servidores distintos: PostgreSQL lo resuelve de forma atómica.
- La API convierte el error `23P01` (exclusion_violation) en **409 Conflict** («Ese hueco acaba de ocuparse, elige otro»).
- Las validaciones de negocio (dentro del horario, día no cerrado, el profesional hace ese servicio) van en la aplicación, pero **la regla crítica no depende de ellas**.
- **«Cualquier profesional»:** se intenta con el primero libre y, si da 409, con el siguiente.

**Tests que lo demuestran** (JUnit + Testcontainers con PostgreSQL 17 real):
1. 20 hilos reservan el mismo hueco a la vez (salida sincronizada con `CountDownLatch`) → **exactamente 1 éxito y 19 errores 409**.
2. Solapamiento parcial → rechazado.
3. Citas seguidas (`[)`) → permitido.
4. Cancelar y volver a reservar ese hueco → permitido.
5. Mismo hueco con profesionales distintos → permitido.
6. Test E2E con Playwright: dos navegadores reservan el mismo hueco.

---

## 6. Plan de fases

Cada fase termina con: tests en verde, CI en verde, un «Resumen rápido» y **tu aprobación**.

| # | Fase | Contenido | Demostrable al final |
|---|---|---|---|
| 1 | **Análisis** (esta) | Investigación, decisiones, CLAUDE.md | Este documento |
| 2 | **Cimientos** | `git init`, monorepo, esqueletos de Spring y Next, docker-compose (Postgres + Mailpit), Flyway V1, `/actuator/health`, proxy `/api`, CI básico | `docker compose up` + las dos apps se comunican en local |
| 3 | **Despliegue temprano** | Cuentas (las creas tú, yo te guío), Cloud Run + Neon + Vercel, secretos, prueba de humo diaria, plan B probado una vez | Un «hola mundo» de punta a punta **en línea** — validamos el hosting antes de construir encima |
| 4 | **Identidad y multi-tenant** | Usuarios, membresías, login/refresh/logout con cookie, roles, `@TenantId`, tests de aislamiento | Login y «quién soy» con roles |
| 5 | **Catálogo del negocio** | CRUD de servicios, profesionales, horarios y cierres (API + panel) | Un dueño configura su barbería |
| 6 | **Disponibilidad y reserva** ⭐ | Cálculo de huecos, página pública `/[slug]`, crear cita, restricción contra la doble reserva y sus tests de concurrencia | Un cliente reserva de verdad |
| 7 | **Emails y gestión de la cita** | Outbox, confirmación HTML, enlace para cancelar o cambiar, Mailpit en local y Brevo en producción | Ciclo completo por email |
| 8 | **Agenda** | Vista diaria y semanal del dueño, vista del empleado (solo su agenda), marcar completada o no presentada | El panel tiene sentido |
| 9 | **Demo en un clic** | Seed de la barbería ficticia, botones «Entrar como negocio / cliente», bandeja de la demo, reset diario de los datos | Un reclutador lo prueba en 10 segundos |
| 10 | **Calidad y lanzamiento v1.0** | E2E con Playwright, accesibilidad, cabeceras de seguridad, límite de peticiones, README con capturas y diagrama, ADRs, revisión de seguridad | 🚀 **Lanzamiento** (enlace en el portfolio) |
| 11 | Recordatorio 24 h | Job externo (cron) → outbox | |
| 12 | Estadísticas del dueño | Ocupación, servicios top, cancelaciones | |
| 13 | Swagger/OpenAPI documentado | Ejemplos, descripciones, UI pública | |
| 14 | Bilingüe ES/EN | next-intl en el front + mensajes de la API + emails | |
| 15 | Lista de espera | Aviso cuando se libera un hueco | |
| 16 | Exportar a calendario | `.ics` en el email y en el panel | |

> Nota: Swagger (13) puede adelantarse mucho porque springdoc está desde la fase 2 (lo necesitamos para generar los tipos). La fase 13 solo es pulirlo y publicarlo.

---

## Fuentes principales
- Spring Boot 4.0 Migration Guide y 4.1 Release Notes (wiki oficial de spring-projects)
- Blog de spring.io (4.2.0-M1/M2); InfoQ, «Spring Boot 4.1»
- nextjs.org/blog/next-16 y guía de actualización a la v16; Releasebot (16.4.x)
- Vercel changelog: Node.js 26 LTS GA
- neon.com/docs/extensions/btree_gist; guías de precios de Neon 2026
- render.com/articles/platforms-with-a-real-free-tier-for-developers-in-2026
- cloud.google.com/run/pricing
- docs.oracle.com (Always Free Resources: reclamación de instancias inactivas)
- Comparativas de 2026 sobre Koyeb, Render, Supabase, Brevo y Resend (fuentes de terceros: se verifican al desplegar)
- README de springdoc-openapi (v3 para Boot 4); notas de migración de Testcontainers 2
