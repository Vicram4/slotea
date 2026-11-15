import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { AgendaAppointment } from "@/lib/agenda";
import { agenda, appointment, bruno, business } from "@/test/agenda-fixtures";
import { ana, staffMe } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { AgendaView, type AgendaLocation } from "./agenda-view";

const monday: AgendaLocation = { date: "2026-11-23", view: "day" };

/** El dueño, con Ana y Bruno en el equipo, y la agenda que se le pase. Devuelve las consultas hechas. */
function ownerWith(appointments: AgendaAppointment[]) {
  const queries: URLSearchParams[] = [];
  server.use(
    http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
    http.get("/api/v1/business", () => HttpResponse.json(business)),
    http.get("/api/v1/staff", () =>
      HttpResponse.json([ana, { ...ana, id: bruno.id, displayName: "Bruno", serviceIds: [] }]),
    ),
    http.get("/api/v1/appointments", ({ request }) => {
      const query = new URL(request.url).searchParams;
      queries.push(query);
      return HttpResponse.json(agenda({ from: query.get("from")!, to: query.get("to")!, appointments }));
    }),
  );
  return queries;
}

function grid() {
  return screen.findByRole("region", { name: /Agenda del día|Agenda de la semana/ });
}

describe("AgendaView", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("shows the whole team for the day, one column per professional", async () => {
    const queries = ownerWith([
      appointment(),
      appointment({ id: "e2", staffId: bruno.id, staffName: "Bruno", startTime: "12:00", endTime: "12:45" }),
    ]);

    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    const day = await grid();
    expect(within(day).getByText("Ana")).toBeInTheDocument();
    expect(within(day).getByText("Bruno")).toBeInTheDocument();
    expect(within(day).getByRole("button", { name: "10:00–10:30, Laura Pérez, Corte, Confirmada" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "lunes, 23 de noviembre de 2026" })).toBeInTheDocument();
    expect(screen.getByText("2 citas")).toBeInTheDocument();
    expect(Object.fromEntries(queries.at(-1)!)).toEqual({ from: "2026-11-23", to: "2026-11-23" });
  });

  it("moves between days and switches to the week view", async () => {
    ownerWith([]);
    const onNavigate = vi.fn();
    renderWithQuery(<AgendaView location={monday} onNavigate={onNavigate} />);

    await screen.findByText("No hay citas este día.");
    await userEvent.click(screen.getByRole("button", { name: "Día siguiente" }));
    expect(onNavigate).toHaveBeenLastCalledWith({ date: "2026-11-24", view: "day" });
    await userEvent.click(screen.getByRole("button", { name: "Semana" }));
    expect(onNavigate).toHaveBeenLastCalledWith({ date: "2026-11-23", view: "week" });
    await userEvent.click(within(screen.getByRole("radiogroup", { name: "Profesional" })).getByRole("radio", { name: "Bruno" }));
    expect(onNavigate).toHaveBeenLastCalledWith({ date: "2026-11-23", view: "day", staffId: bruno.id });
  });

  it("shows a week of one professional, from Monday to Sunday", async () => {
    const queries = ownerWith([appointment({ id: "e3", date: "2026-11-26", startTime: "17:00", endTime: "17:30" })]);

    renderWithQuery(<AgendaView location={{ date: "2026-11-26", view: "week" }} onNavigate={vi.fn()} />);

    const week = await grid();
    expect(screen.getByRole("heading", { name: "23 – 29 nov 2026" })).toBeInTheDocument();
    expect(within(week).getByText("26 nov")).toBeInTheDocument();
    expect(within(week).getByRole("button", { name: /^17:00–17:30, Laura Pérez/ })).toBeInTheDocument();
    // Sin profesional elegido, la semana es la del primero del equipo.
    expect(Object.fromEntries(queries.at(-1)!)).toEqual({ from: "2026-11-23", to: "2026-11-29", staffId: ana.id });
  });

  it("hides cancelled appointments unless asked", async () => {
    ownerWith([appointment(), appointment({ id: "e4", status: "CANCELLED", customer: { ...appointment().customer, fullName: "Pablo Ruiz" } })]);
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    const day = await grid();
    expect(within(day).queryByText("Pablo Ruiz")).not.toBeInTheDocument();
    await userEvent.click(screen.getByLabelText("Mostrar canceladas (1)"));
    expect(within(day).getByRole("button", { name: /Pablo Ruiz, Corte, Cancelada/ })).toBeInTheDocument();
  });

  it("marks how a past appointment went and can undo it", async () => {
    let body: unknown;
    const past = appointment({ canCancel: false, canRecordOutcome: true });
    ownerWith([past]);
    server.use(
      http.put(`/api/v1/appointments/${past.id}/status`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...past, status: (body as { status: string }).status });
      }),
    );
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    await userEvent.click(within(await grid()).getByRole("button", { name: /Laura Pérez/ }));
    const dialog = await screen.findByRole("dialog", { name: "Laura Pérez" });
    expect(within(dialog).getByText("Corte con Ana")).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "+34 600 000 000" })).toHaveAttribute("href", "tel:+34600000000");
    expect(within(dialog).queryByRole("button", { name: "Cancelar cita" })).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole("button", { name: "No se presentó" }));
    expect(await within(dialog).findByText("No se presentó", { selector: "span" })).toBeInTheDocument();
    expect(body).toEqual({ status: "NO_SHOW" });

    await userEvent.click(within(dialog).getByRole("button", { name: "Deshacer" }));
    expect(await within(dialog).findByText("Confirmada")).toBeInTheDocument();
    expect(body).toEqual({ status: "CONFIRMED" });
  });

  it("cancels a future appointment only after confirming", async () => {
    let cancelled = false;
    const future = appointment();
    ownerWith([future]);
    server.use(
      http.post(`/api/v1/appointments/${future.id}/cancel`, () => {
        cancelled = true;
        return HttpResponse.json({ ...future, status: "CANCELLED", canCancel: false, cancelledAt: "2026-11-20T10:00:00Z" });
      }),
    );
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    await userEvent.click(within(await grid()).getByRole("button", { name: /Laura Pérez/ }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).queryByText("¿Cómo fue la cita?")).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancelar cita" }));
    expect(within(dialog).getByText(/Se avisará a Laura Pérez por email/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "No, mantenerla" }));
    expect(cancelled).toBe(false);

    await userEvent.click(within(dialog).getByRole("button", { name: "Cancelar cita" }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Sí, cancelar la cita" }));
    expect(await within(dialog).findByText("Cancelada", { selector: "span" })).toBeInTheDocument();
    expect(cancelled).toBe(true);
    expect(within(dialog).queryByRole("button", { name: "Cancelar cita" })).not.toBeInTheDocument();
  });

  it("explains why a change was rejected", async () => {
    const future = appointment();
    ownerWith([future]);
    server.use(
      http.post(`/api/v1/appointments/${future.id}/cancel`, () =>
        HttpResponse.json(
          { status: 409, detail: "La cita ya ha empezado: márcala como completada o no presentada." },
          { status: 409 },
        ),
      ),
    );
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    await userEvent.click(within(await grid()).getByRole("button", { name: /Laura Pérez/ }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancelar cita" }));
    await userEvent.click(within(dialog).getByRole("button", { name: "Sí, cancelar la cita" }));

    expect(await within(dialog).findByText("La cita ya ha empezado: márcala como completada o no presentada.")).toBeInTheDocument();
  });

  it("shows an employee only their agenda, without choosing a professional", async () => {
    let query: URLSearchParams | undefined;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      http.get("/api/v1/business", () => HttpResponse.json(business)),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
      http.get("/api/v1/appointments", ({ request }) => {
        query = new URL(request.url).searchParams;
        return HttpResponse.json(agenda({ staff: [{ id: ana.id, displayName: "Ana", active: true }], appointments: [appointment()] }));
      }),
    );
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    expect(within(await grid()).getByRole("button", { name: /Laura Pérez/ })).toBeInTheDocument();
    expect(screen.queryByLabelText("Profesional")).not.toBeInTheDocument();
    expect(query?.has("staffId")).toBe(false);
  });

  it("tells an employee without a professional profile what to do", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      http.get("/api/v1/business", () => HttpResponse.json(business)),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
      http.get("/api/v1/appointments", () => HttpResponse.json(agenda({ staff: [] }))),
    );
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    expect(await screen.findByText(/Tu usuario aún no está enlazado a una ficha de profesional/)).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Agenda del día" })).not.toBeInTheDocument();
  });

  it("lists the appointments for small screens too", async () => {
    // La API las devuelve por hora de inicio.
    ownerWith([
      appointment({ id: "e5", staffId: bruno.id, staffName: "Bruno", startTime: "09:00", endTime: "09:30" }),
      appointment(),
    ]);
    renderWithQuery(<AgendaView location={monday} onNavigate={vi.fn()} />);

    const list = await screen.findByRole("region", { name: "Lista de citas" });
    const items = within(list).getAllByRole("button");
    expect(items.map((item) => item.textContent)).toEqual([
      "09:00Laura Pérez09:30Corte · Bruno · Confirmada",
      "10:00Laura Pérez10:30Corte · Ana · Confirmada",
    ]);
  });
});
