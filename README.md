# Slotea

Plataforma de reservas de citas para pequeños negocios (peluquerías, barberías, centros de estética…). Multi-tenant: cada negocio tiene su página pública de reservas y su panel.

> 🚧 En construcción. El README completo (capturas, diagrama y demo) llega con la v1.0.

**En producción:** web en https://slotea.vercel.app (Vercel) · API en Cloud Run · PostgreSQL en Neon. Cada push a `main` con la CI en verde se despliega solo; una prueba de humo diaria vigila que todo responda. Detalles en [infra/](infra/).

## Stack

| Parte | Tecnología |
|---|---|
| API | Java 25 · Spring Boot 4.1 · Spring Security 7 (JWT en cookie) · Spring Data JPA · Flyway · springdoc-openapi |
| Base de datos | PostgreSQL 17 (multi-tenant con `@TenantId` de Hibernate) |
| Web | Next.js 16 (App Router) · TypeScript · Tailwind 4 + shadcn/ui · TanStack Query · cliente tipado desde OpenAPI |
| Tests | JUnit 5 + Testcontainers · Vitest + Testing Library + MSW |

Cómo funcionan la sesión y el aislamiento entre negocios: [docs/fase-4-identidad.md](docs/fase-4-identidad.md).
| CI | GitHub Actions |

## Estructura

```
apps/api     API REST (Spring Boot)
apps/web     Frontend (Next.js); /api/* se reenvía a la API
infra/       Despliegue (Cloud Run, Render)
docs/        Análisis y decisiones
compose.yaml PostgreSQL + Mailpit para desarrollo local
```

## Arrancar en local

Requisitos: JDK 25, Node 26, pnpm y Docker.

```bash
cp .env.example .env                          # pon POSTGRES_PASSWORD y JWT_SECRET (openssl rand -base64 48)
cp apps/web/.env.example apps/web/.env.local
docker compose up -d                          # PostgreSQL + Mailpit

cd apps/api && ./mvnw spring-boot:run         # API en http://localhost:8080
cd apps/web && pnpm install && pnpm dev       # Web en http://localhost:3000
```

## Tests

```bash
cd apps/api && ./mvnw verify                  # incluye tests contra PostgreSQL real (Testcontainers)
cd apps/web && pnpm lint && pnpm typecheck && pnpm test
```

Si cambias la API, actualiza el contrato y los tipos de la web:

```bash
cd apps/api && ./mvnw test -Dtest=OpenApiContractTests -Dopenapi.update=true   # → apps/web/openapi.json
cd apps/web && pnpm api:types                                                  # → src/lib/api/schema.d.ts
```
