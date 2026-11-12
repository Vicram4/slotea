# Fase 4 — Identidad y multi-tenant

Cómo inicia sesión un usuario, cómo viaja la sesión y cómo se garantiza que un negocio nunca ve los datos de otro.

## Modelo de datos (migración `V2`)

```
business       el tenant: slug único (URL pública), zona horaria, email de contacto
app_user       identidad global; email único y siempre en minúsculas (CHECK en la BD)
membership     usuario ↔ negocio con rol OWNER | STAFF        ← primera tabla con @TenantId
refresh_token  solo el SHA-256 del token; family_id agrupa los tokens de una sesión
```

Un usuario puede pertenecer a varios negocios (el modelo lo permite desde ya). Un cliente será un `app_user` sin membresía (fase 6).

## Sesión: JWT en cookie + refresh rotatorio

| Cookie | Contenido | Atributos |
|---|---|---|
| `access_token` | JWT HS256, 15 min. Claims: `sub` (usuario), `bid` (negocio activo), `role` | `HttpOnly; Secure; SameSite=Lax; Path=/` |
| `refresh_token` | 256 bits aleatorios (en la BD, su SHA-256) | `HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth`, 30 días |
| `XSRF-TOKEN` | token CSRF (`csrf.spa()`), legible por la SPA | `Path=/` |

```
Navegador ──/api/*──► Next (rewrite) ──► API        (mismo dominio: cookies first-party, sin CORS)

login/signup ─► Set-Cookie access + refresh
petición     ─► 200   ·  401 (JWT caducado) ─► POST /auth/refresh ─► rota el refresh ─► repite la petición
logout       ─► revoca la familia del refresh y borra las cookies
```

Decisiones:

- **El navegador nunca ve los tokens.** Están en cookies `HttpOnly`, no en `localStorage`, así que un XSS no puede robarlos.
- **HS256 y no RSA.** El mismo servicio firma y valida. La clave (`JWT_SECRET`) está en Secret Manager, y si falta o es corta la API no arranca.
- **Rotación con detección de robo.** Cada refresh revoca el token usado y emite otro de la misma familia. Si alguien presenta un token ya rotado, se revoca la familia entera: o lo han copiado, o el cliente se ha desincronizado. El «revocar si sigue activo» es un `UPDATE … WHERE revoked_at IS NULL` atómico, de modo que de dos refresh simultáneos con el mismo token solo uno puede ganar (hay un test con 8 hilos).
- **Refresh *single-flight* en la web.** Si varias peticiones reciben 401 a la vez, comparten una sola renovación (`authFetch`). Sin esto, dos pestañas cerrarían la sesión al dispararse la detección de robo.
- **Las membresías se releen en cada refresh.** Si a alguien le quitan el acceso, lo pierde como mucho cuando caduque su JWT (15 min).
- **Login sin filtrar información.** El mensaje es el mismo si el email no existe o si la contraseña está mal, y en el primer caso se compara contra un hash ficticio para que el tiempo de respuesta también sea igual.
- **Las rutas públicas no leen la cookie del JWT.** Una cookie caducada o manipulada no impide hacer login, refrescar la sesión ni ver páginas públicas.

### Trampa: CSRF con el token en una cookie

El resource server de Spring Security **excluye del CSRF las peticiones que traen un bearer token**. Tiene sentido cuando el token va en la cabecera `Authorization`, porque el navegador no la añade solo. Aquí el token va en una cookie, que el navegador sí envía automáticamente, y esa exclusión dejaba el panel expuesto a CSRF. La configuración (`SecurityConfig`) vuelve a aplicar la regla estándar a todas las peticiones, y `AuthFlowTests.authenticatedRequestsStillRequireCsrf` impide que vuelva a pasar.

## Multi-tenant: tres capas

1. **Aplicación (`@TenantId` de Hibernate).** `TenantFilter` lee el claim `bid` del JWT y lo fija en un `ScopedValue` (Java 25) durante la petición. Al abrir cada sesión, Hibernate añade `business_id = ?` a todas las consultas de las entidades con `@TenantId` y rellena la columna al insertar.
   - **Fail closed:** sin tenant, Hibernate recibe un id que no existe y las consultas devuelven vacío, no los datos de todos.
   - Las operaciones que cruzan negocios (login, registro, «mis negocios») usan `SystemTransactions`, que abre una transacción con el tenant raíz y se niega a ejecutarse dentro de otra transacción (el tenant se fija al abrir la sesión).
   - Los endpoints nunca reciben un `businessId` del cliente: el negocio sale siempre del JWT.
2. **Base de datos.** FKs a `business` y `UNIQUE (business_id, id)` para que las tablas de las siguientes fases usen FKs compuestas.
3. **Tests.** `TenantIsolationTests` (por HTTP: A no ve los datos de B → **404**, nunca 403) y `TenancyTests` (por repositorio: sin tenant no se ve nada, un tenant no puede insertar en otro negocio). Cada endpoint nuevo añade su caso usando `AuthApi`.

## Roles

`ROLE_OWNER > ROLE_STAFF` (jerarquía de roles) y `@PreAuthorize` en cada endpoint. STAFF puede ver el negocio; solo OWNER lo modifica y ve el equipo.

## Contrato API ↔ web

springdoc genera el OpenAPI. `OpenApiContractTests` lo guarda en `apps/web/openapi.json` y falla si queda desactualizado. La web genera sus tipos con `pnpm api:types`, y la CI comprueba que coinciden. Un `ModelConverter` lleva la nulabilidad de los `record` (JSpecify `@Nullable`) al contrato, de modo que en TypeScript un campo es obligatorio salvo que la API diga que puede faltar.

## Fuera de esta fase

- Invitar empleados (necesita email: fase 7) y registro de clientes (fase 6).
- Elegir el negocio activo cuando el usuario pertenece a varios.
- Límite de intentos de login (Bucket4j, fase 10).
