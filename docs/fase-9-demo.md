# Fase 9 — Demo en un clic

Quien entra en la portada puede probar Slotea en segundos, sin registrarse. Elige un **sector** (barbería, peluquería y estética, taller, psicología, fisioterapia o veterinaria) y entra como **negocio**, como **empleado** o como **cliente** en un negocio ficticio con la agenda llena. No se envía ningún email: los ve en una **bandeja de la demo**.

## Decisión: una copia por visitante

Cada clic crea **una copia propia** del negocio de demostración (`is_demo = true`, slug `demo-xxxxxxxx`), que se borra a las 24 h.

| | Una copia por visitante (elegida) | Una demo compartida con reset diario |
|---|---|---|
| Qué ve el visitante | Siempre una agenda limpia y coherente | Lo que hayan dejado otros |
| Vandalismo | No afecta a nadie más | Estropea la demo a todos hasta el reset |
| Coste | Unas 500 filas por clic (≈ 0,6 s en local) y una limpieza | Un job de reset |

De paso demuestra el multi-tenant: cada clic es un tenant nuevo y aislado (hay un test que lo comprueba).

## El seed: plantillas por sector (fase 10)

Todo es ficticio y se genera desde el código. Las fechas son **relativas a hoy**, en la zona del negocio (Madrid).

El seed tiene dos piezas:

- **`DemoTemplate`** guarda solo datos: el catálogo, los profesionales (con o sin cuenta, qué servicios hacen y su **horario semanal propio**), los nombres de los clientes, las notas y cada cuánto se prueba a colocar una cita. Su constructor comprueba que la plantilla es coherente: un dueño, como mucho un empleado con cuenta, ningún servicio sin alguien que lo haga… Una plantilla mal escrita falla al arrancar, no al crear una copia.
- **`DemoSeeder`** es el motor, el mismo para todos los sectores. Convierte una plantilla en una agenda.

Las seis plantillas están en **`DemoTemplates`**. Cada sector enseña algo distinto del producto:

| Sector | Profesionales | Qué enseña |
|---|---|---|
| Barbería | 3 | La demo original: citas cortas y agenda llena |
| Peluquería y estética | 3 (la esteticista, con su propio catálogo) | Un tinte de 2 h no cabe en un hueco de 30. Cierra los lunes |
| Taller mecánico | 3 | Citas de horas y un margen después del cambio de aceite |
| Psicología | **1 (la dueña)** | Sesiones de 50 min + 10 de margen, siempre en punto. **No hay empleado** |
| Fisioterapia | 3 | Turnos distintos: mañana, tarde y jornada partida |
| Veterinaria | 3 | Margen para limpiar la consulta y peluquería canina aparte |

En todos los sectores:

- Cada sector tiene su equipo con nombres de pila ficticios (en la barbería, Toni, Rubén y Nacho): el primero es el dueño, el segundo tiene **cuenta de empleado enlazada a su ficha** (salvo en psicología, donde Inés trabaja sola) y el tercero no tiene cuenta.
- Hay un festivo del negocio y, si hay empleado, unas vacaciones suyas.
- Los clientes son ficticios («Lucía Ejemplo», «Mario Prueba»…), con emails `@example.com` (dominio reservado) y sin teléfono.
- Citas desde hace 14 días hasta dentro de 14 días:
  - **las pasadas**: completadas, no presentadas o canceladas;
  - **las de los últimos 3 días**: casi todas **sin marcar**, para probar las acciones de la agenda. Cada profesional con cuenta (dueño y empleado) tiene siempre al menos una, sea cual sea la hora a la que se cree la copia;
  - **las futuras**: confirmadas (alguna cancelada), con ocupación decreciente, para que queden huecos que reservar.
- `Random` con **semilla fija**: el contenido es reproducible y los tests también.
- SQL por lotes (`JdbcTemplate.batchUpdate`) en **una transacción**. Las citas pasan por la restricción `appointment_no_overlap`: si el generador se equivocara, el seed fallaría.
- El hash del enlace de cada cita es el mismo HMAC que en una reserva real. Por eso, cuando el negocio cancela una cita del seed, el email que se genera lleva un enlace que funciona.

## API

| Endpoint | Quién | Qué hace |
|---|---|---|
| `POST /api/v1/demo/sessions` `{sector, role: OWNER\|STAFF}` | Público (con CSRF) | Crea una copia del sector y abre sesión en ella (las cookies de siempre). **400** si se pide `STAFF` en psicología. **503** si hay 100 copias vivas |
| `POST /api/v1/demo/switch` `{role}` | Sesión en una copia | Cambia de dueño a empleado (o al revés) **en la misma copia**. 404 en un negocio real |
| `GET /api/v1/demo/inbox` | Dueño de una copia | Los últimos 50 emails de la copia, con su asunto |
| `GET /api/v1/demo/inbox/{id}` | Dueño de una copia | El email renderizado (HTML y texto) con las plantillas reales |
| `POST /internal/jobs/demo-cleanup` | Cron (`X-Jobs-Token`) | Borra las copias de más de 24 h |

- El **cliente** no tiene cuenta: «Reservar como cliente» crea la copia (sesión de dueño) y abre su página pública.
- **Sin contraseña.** Los usuarios de la demo tienen un hash que ningún login acepta. Solo se entra con `AuthService.demoSession`, que comprueba que el negocio es `is_demo` y que el usuario es miembro.
- **Prefijo reservado.** Un negocio real no puede registrarse con un slug `demo-…`.
- **La bandeja es solo para el dueño.** Los emails van al cliente y al negocio, no a los empleados.
- **Tope de copias.** Cuando se llega a 100, la propia petición intenta limpiar antes de rendirse, por si el cron no ha pasado.
- **`MeResponse` y la página pública** traen `demo`: la web muestra la barra y la bandeja sin repetir reglas.
- **`demoCanSwitch`** (en `MeResponse`) indica si la copia tiene una cuenta con el otro rol. Lo calcula la consulta de membresías, así que la barra solo ofrece «Ver como empleado» cuando existe ese empleado (en psicología no).

## Emails: nunca salen de la demo

En la migración `V7`, la outbox tiene un estado nuevo: **`DEMO`**.

- `EmailOutbox.enqueue` lo decide en el propio `INSERT … SELECT … FROM business`, así que ningún camino del código puede olvidarse de comprobarlo.
- El envío solo reclama los `PENDING`, de modo que un email `DEMO` no se envía nunca: ni al momento ni con el job.
- **Por qué:** los clientes de la demo son ficticios (los rebotes perjudican al remitente en Brevo) y nadie debe poder usar la demo para mandar correos a direcciones ajenas. Por eso, quien prueba la demo no recibe nada aunque reserve con su email real.
- Los **avisos al negocio** también se encolan en la demo (en la fase 8 se descartaban): así se ven los dos lados.

## Limpieza

- `DemoService.deleteExpired` borra primero los usuarios de las copias caducadas, solo si no pertenecen a ningún negocio real. Después borra el negocio, y la cascada se lleva todo lo demás: citas, outbox, refresh tokens…
- La lanza `jobs.yml` cada hora, junto a la outbox. La API y Neon ya están despiertos, así que no suma consumo.

## Web

- **Portada:** tarjeta «Prueba la demo» en dos pasos.
  - **Primero, el sector:** seis tarjetas con una línea propia cada una. Los textos están en `demo-sectors.ts`, un `Record<DemoSector, …>` sobre el tipo generado: si la API añade un sector, TypeScript obliga a darle textos. No se piden a la API porque la portada tendría que esperar a que despierte.
  - **Después, cómo entrar:** negocio, empleado o cliente. En psicología, sin empleado. El foco pasa al título del paso; con «Otro sector» vuelve a la tarjeta elegida.
  - Si la API tarda más de 2,5 s, avisa: «Despertando el servidor…».
  - Si la demo está llena, muestra el mensaje de la API.
- **Barra de la demo** en el panel: «Ver como empleado / dueño», «Reservar como cliente» y «Bandeja de la demo».
  - Al cambiar de usuario se vacía la caché de TanStack Query, como en el logout.
- **`/panel/bandeja`:** lista de emails («Al cliente» / «Al negocio») y vista previa en un `iframe` con `sandbox` (sin scripts ni acceso a la sesión). Los enlaces se abren en otra pestaña.
- **Página pública de una copia:**
  - aviso de que no se envían emails;
  - botón «Rellenar con datos de prueba» (`cliente.demo@example.com`);
  - confirmación que lleva a la bandeja.

## Tests

- **`DemoSeederTests`** (**cada sector** en cuatro instantes fijos: un domingo, un lunes antes de abrir y las dos semanas con cambio de hora):
  - todas las citas dentro del horario de su profesional en hora local;
  - cada cita, de un servicio que ese profesional hace;
  - ninguna en el festivo ni en las vacaciones;
  - todas en la rejilla de cuartos de hora;
  - estados coherentes con el pasado y el futuro;
  - cada profesional con cuenta, con alguna cita sin marcar;
  - psicología: una sola profesional, sin vacaciones de empleado y sesiones de 50 + 10 en punto.
- **`DemoApiTests`:**
  - entrar como dueño o como empleado (que solo ve lo suyo y puede marcar);
  - cada sector abre su negocio, con algo que marcar y huecos para reservar;
  - psicología: 400 al entrar como empleado y 404 al cambiar a empleado. `demoCanSwitch` solo vale `true` si hay empleado;
  - el sector es obligatorio y tiene que existir;
  - cambiar de rol;
  - nada de esto funciona en un negocio real;
  - CSRF y sesión;
  - sin login con contraseña;
  - prefijo `demo-` reservado;
  - una reserva acaba en la bandeja y no en Mailpit;
  - bandeja solo para el dueño;
  - **aislamiento entre dos copias** (404 en citas y emails);
  - limpieza (borra copia y usuarios; deja las recientes; sesión inválida);
  - token del job;
  - 503 con el tope.
- **`EmailOutboxTests` / `AgendaApiTests`:** un negocio demo encola los dos emails como `DEMO` y no envía ninguno.
- **Web (Vitest + MSW):**
  - lanzador: los seis sectores; las tres entradas con el sector en la petición; psicología sin empleado; el foco y «Otro sector»; el aviso de arranque, el 503 y el 429;
  - barra de la demo y cambio de rol (sin él cuando no hay empleado);
  - bandeja: lista, vista previa, vacía y solo para el dueño;
  - página pública en demo.

## Fuera de esta fase
- Límite de peticiones por IP (Bucket4j, fase 10). Ahora solo hay un tope global de copias.
- Área «mis citas» del cliente con cuenta: el cliente de la demo es la página pública.
- README con capturas, y borrar `peluqueria-prueba` de producción (fase 10).
