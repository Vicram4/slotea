# Cambios

## 1.0.0

Primera versión completa, en producción en https://slotea.vercel.app.

### Para los negocios
- **Registro y panel** con dos roles: dueño y empleado.
- **Catálogo:** servicios con duración, margen después de cada cita y precio; profesionales con sus
  servicios, horario semanal (también jornada partida) y ausencias; cierres de todo el negocio.
- **Agenda** del día (columnas por profesional) y de la semana, en ordenador y en el móvil: marcar cómo fue
  cada cita (completada o no se presentó) y cancelarla. El empleado solo ve y gestiona lo suyo.
- **Avisos por email** al negocio de cada reserva, cambio o cancelación.
- **Color de marca** que llevan la página de reservas y el panel, con comprobación de contraste.

### Para sus clientes
- **Página pública de reservas** sin cuenta: servicio, profesional (o «me da igual») y hora, con huecos
  calculados en la zona horaria del negocio y que respetan la duración de cada servicio.
- **Email de confirmación con un enlace** para cambiar la hora o cancelar la cita.

### Demo
- **Demo en un clic** en seis sectores, como negocio, empleado o cliente. Cada visitante tiene su copia
  (se borra a las 24 h) y los emails se ven en una bandeja dentro de la app.

### Por dentro
- Doble reserva imposible por una restricción de PostgreSQL, probada con 20 reservas simultáneas.
- Aislamiento entre negocios en tres capas (Hibernate, claves foráneas compuestas y tests).
- Sesión con JWT y refresh rotatorio en cookies `httpOnly`; CSRF; límites de peticiones; CSP.
- Emails con outbox transaccional y reintentos; trabajos periódicos desde un cron externo.
- Tests: JUnit + Testcontainers, Vitest, Playwright (recorridos principales) y axe (WCAG 2.2 AA).
- Despliegue continuo a Cloud Run, Neon y Vercel con la CI en verde; prueba de humo diaria y latido
  semanal del email.

Decisiones razonadas en [docs/adr](docs/adr/README.md).
