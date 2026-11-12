# CLAUDE.md — Reglas del proyecto

Plataforma de reservas de citas multi-tenant **Slotea**. Es un proyecto de portfolio: el código, los tests y el README tienen que poder enseñarse en una entrevista.
El análisis y las decisiones están en [docs/fase-1-analisis.md](docs/fase-1-analisis.md). Consúltalo antes de cambiar el stack o la arquitectura.

## Forma de trabajar (obligatorio)
- **Idioma:** respuestas en español. Explica brevemente qué haces y por qué. Cuando el usuario tenga que actuar, dale instrucciones paso a paso.
- **Por fases:** no empieces la siguiente fase sin la aprobación explícita del usuario. Cada fase termina con un **«Resumen rápido»** al principio del mensaje.
- **Opinión:** recomienda una opción en lugar de dar solo una lista.
- **No inventes** datos personales ni de negocio (nombres, teléfonos, direcciones reales…). Si falta información, pregunta. Los datos de la demo son claramente ficticios.
- **Pide permiso antes de:** instalar dependencias, borrar archivos importantes, hacer commit/push, publicar o desplegar, y crear cuentas o recursos en la nube.
- **Alcance:** trabaja solo en `c:\dev\slotea`. No toques otros proyectos de `c:\dev`.
- **Secretos:** solo en archivos `.env*` ignorados por Git (y en los gestores de secretos de cada plataforma). Nunca en el código, en los commits ni en el chat. Mantén un `.env.example` sin valores reales.
- **Tokens:** avisa cuando convenga hacer `/clear` y deja preparado el prompt para continuar.

## Stack (versiones cerradas en la fase 1)
- **API:** Java 25 LTS · Spring Boot 4.1.x · Spring Security 7 · Spring Data JPA (Hibernate 7) · Flyway (`spring-boot-starter-flyway`) · springdoc-openapi 3 · Maven Wrapper.
- **BD:** PostgreSQL 17 (Docker en local, Neon en producción).
- **Web:** Node 26 LTS · Next.js 16.4.x (App Router) · TypeScript strict · Tailwind 4 + shadcn/ui · TanStack Query 5 · react-hook-form + Zod 4 · openapi-fetch · pnpm.
- **Tests:** JUnit + Testcontainers 2 · Vitest + Testing Library + MSW · Playwright.
- **Infra:** Docker · GitHub Actions · Cloud Run (API) · Neon (BD) · Vercel (web) · Brevo SMTP (email) · Mailpit en local.

## Trampas conocidas de las versiones
- Boot 4: starters modulares (`spring-boot-starter-webmvc`, `...-security-oauth2-resource-server`, `...-flyway`) y uno de test por tecnología.
- Boot 4: **Jackson 3** → `tools.jackson.*` (las anotaciones siguen en `com.fasterxml.jackson.annotation`).
- Boot 4: `@MockitoBean`/`@MockitoSpyBean` (ya no existe `@MockBean`). `@SpringBootTest` necesita `@AutoConfigureMockMvc`/`@AutoConfigureRestTestClient`. Usa `RestClient`, no `RestTemplate`.
- Spring Security 7: solo DSL con lambdas.
- Testcontainers 2: `testcontainers-postgresql`, paquete `org.testcontainers.postgresql`.
- Next 16: `params`, `searchParams`, `cookies()` y `headers()` son **async**. `next lint` ya no existe (se usa `eslint`).
- Security: el resource server **excluye del CSRF las peticiones con bearer token**; como nuestro JWT va en cookie, `SecurityConfig` lo vuelve a exigir. No lo quites.
- Tests: no uses `SecurityMockMvcRequestPostProcessors.csrf()` (cambia el repositorio CSRF del contexto compartido); usa `SpaCsrf.spaCsrf()`. Tests web con `@WebMvcTest` → `@Import(WebSliceSecurity.class)`.
- Tenancy: el tenant se fija al **abrir la sesión** de Hibernate; para cruzar negocios, `SystemTransactions` (nunca dentro de otra transacción).
- MSW 3: la opción es `onUnhandledFrame` (no `onUnhandledRequest`). pnpm rechaza versiones publicadas hace menos de 24 h: no añadas excepciones, usa la anterior.
- Ante la duda, **lee la documentación de la versión instalada** antes de escribir código.

## Arquitectura
- Monorepo: `apps/api`, `apps/web`, `infra/`, `docs/`, `.github/workflows/`.
- API organizada **por funcionalidad** (`auth`, `business`, `catalog`, `schedule`, `booking`, `notification`, `demo`, `shared`). DTOs como `record`. Sin Lombok.
- REST `/api/v1`, errores en `ProblemDetail` (RFC 9457). El contrato es el OpenAPI de springdoc y los tipos de TS se generan desde él (nunca se escriben a mano).
- El navegador solo habla con Vercel: `/api/*` llega a la API por `rewrites` (cookies *first-party*, sin CORS).
- Auth: JWT de acceso de 15 min + refresh token rotatorio (hasheado en la BD), ambos en cookies `httpOnly; Secure; SameSite=Lax`. CSRF con `csrf.spa()`.
- La API escala a cero, así que **no usamos `@Scheduled`** para trabajos de negocio: se lanzan desde un cron externo contra `/internal/jobs/*` con un token.

## Reglas de datos (no negociables)
- **Multi-tenant:** toda tabla de negocio lleva `business_id`, usa `@TenantId` de Hibernate y FKs compuestas `(business_id, id)`. Cada endpoint nuevo lleva su **test de aislamiento** (el negocio A no ve nada del B → 404).
- **Doble reserva:** la garantiza la restricción `EXCLUDE USING gist` de `appointment`. No se quita ni se debilita. `23P01` → HTTP 409.
- Se permiten **reservas de invitado** (solo email + enlace con token hasheado).
- Las fechas se guardan en `timestamptz` (UTC). Los huecos se calculan en la zona horaria del negocio. Hay tests del cambio de hora.
- Los tokens (refresh, enlace de la cita) se guardan **hasheados**.
- Esquema **solo con migraciones de Flyway**: nunca `ddl-auto=update`. Una migración ya aplicada no se edita: se crea otra.

## Calidad
- Todo cambio de lógica lleva test. La concurrencia de reservas se prueba con PostgreSQL real (Testcontainers), nunca con H2.
- CI en verde antes de dar una fase por terminada.
- Commits pequeños en estilo Conventional Commits (`feat:`, `fix:`, `test:`…), en inglés.
- El código y los identificadores van en inglés. La interfaz va en español (ES/EN en la fase 14). Los documentos para el usuario, en español.

## Comandos
Requisitos: JDK 25, Node 26 (`.node-version`, con fnm), pnpm, Docker. Primera vez: copia `.env.example` → `.env` y `apps/web/.env.example` → `apps/web/.env.local`.
- Servicios locales (PostgreSQL + Mailpit): `docker compose up -d` (raíz). Mailpit: http://localhost:8025
- API: `cd apps/api` → `./mvnw spring-boot:run` (arranca) · `./mvnw verify` (tests, necesita Docker)
- Web: `cd apps/web` → `pnpm dev` · `pnpm lint` · `pnpm typecheck` · `pnpm test` · `pnpm build`
- Contrato tras cambiar la API: `./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true` (en `apps/api`) y luego `pnpm api:types` (en `apps/web`).
- Swagger UI (local): `http://localhost:<PORT>/swagger-ui.html` · OpenAPI: `/v3/api-docs`
- En este equipo los puertos son 5433 (BD) y 8081 (API) porque 5432/8080 los usa otro proyecto; se cambian en `.env`.
- Herramienta Bash de Claude: el `.bashrc` redefine `cd` (fnm); usar `builtin cd`. Para Node, mejor PowerShell con `$env:APPDATA\fnm\node-versions\v26.13.1\installation` en el `PATH`.
