# Slotea

Plataforma de reservas de citas para pequeños negocios (peluquerías, barberías, centros de estética…). Multi-tenant: cada negocio tiene su página pública de reservas y su panel.

> 🚧 En construcción. El README completo (capturas, diagrama y demo) llega con la v1.0.

## Stack

| Parte | Tecnología |
|---|---|
| API | Java 25 · Spring Boot 4.1 · Spring Data JPA · Flyway · springdoc-openapi |
| Base de datos | PostgreSQL 17 |
| Web | Next.js 16 (App Router) · TypeScript · Tailwind 4 |
| Tests | JUnit 5 + Testcontainers · Vitest + Testing Library |
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
cp .env.example .env                          # pon una contraseña en POSTGRES_PASSWORD
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
