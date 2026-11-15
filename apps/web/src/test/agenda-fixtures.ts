import { http, HttpResponse } from "msw";

import type { Agenda, AgendaAppointment } from "@/lib/agenda";
import type { Schemas } from "@/lib/api/client";

import { ana } from "./catalog-fixtures";

/** Datos ficticios para los tests de la agenda. */

export const business: Schemas["BusinessResponse"] = {
  id: "0193a1b2-0000-7000-8000-0000000000b1",
  slug: "barberia-demo",
  name: "Barbería Demo",
  timezone: "Europe/Madrid",
  contactEmail: "negocio@example.test",
  brandColor: "#18181b",
  demo: false,
  createdAt: "2026-11-01T10:00:00Z",
};

export const bruno = { id: "0193a1b2-0000-7000-8000-00000000a003", displayName: "Bruno", active: true };

/** Una cita confirmada del lunes 23 de noviembre de 2026 con Ana; se cambia lo que haga falta. */
export function appointment(overrides: Partial<AgendaAppointment> = {}): AgendaAppointment {
  return {
    id: "0193a1b2-0000-7000-8000-00000000e001",
    status: "CONFIRMED",
    staffId: ana.id,
    staffName: "Ana",
    serviceId: "0193a1b2-0000-7000-8000-00000000c001",
    serviceName: "Corte",
    customer: { fullName: "Laura Pérez", email: "laura@example.test", phone: "+34 600 000 000" },
    startsAt: "2026-11-23T09:00:00Z",
    endsAt: "2026-11-23T09:30:00Z",
    date: "2026-11-23",
    startTime: "10:00",
    endTime: "10:30",
    notes: null,
    createdAt: "2026-11-15T11:03:00Z",
    cancelledAt: null,
    canCancel: true,
    canRecordOutcome: false,
    ...overrides,
  };
}

export function agenda(overrides: Partial<Agenda> = {}): Agenda {
  return {
    timezone: "Europe/Madrid",
    from: "2026-11-23",
    to: "2026-11-23",
    staff: [{ id: ana.id, displayName: "Ana", active: true }, bruno],
    appointments: [],
    ...overrides,
  };
}

/** Negocio + agenda vacía: para las pantallas que solo enseñan un resumen de hoy. */
export function emptyAgendaHandlers() {
  return [
    http.get("/api/v1/business", () => HttpResponse.json(business)),
    http.get("/api/v1/appointments", () => HttpResponse.json(agenda({ staff: [] }))),
  ];
}
