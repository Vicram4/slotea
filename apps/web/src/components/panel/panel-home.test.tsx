import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { agenda, appointment, business, emptyAgendaHandlers } from "@/test/agenda-fixtures";
import { ana, corte, staffMe } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { PanelHome } from "./panel-home";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

describe("PanelHome", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("shows who I am and what is left to set up", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      ...emptyAgendaHandlers(),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.get("/api/v1/staff", () => HttpResponse.json([])),
    );

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText("Hola, Ana Demo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /\/barberia-demo$/ })).toHaveAttribute("href", "/barberia-demo");
    expect(screen.getByText(/Dueño en Barbería Demo/)).toBeInTheDocument();
    expect(await screen.findByText("Te quedan 2 pasos para que tus clientes puedan reservar.")).toBeInTheDocument();
    const steps = await screen.findAllByRole("listitem");
    expect(steps.map((step) => step.textContent)).toEqual([
      "Crea los servicios que ofreces(hecho)",
      "Añade a tu equipo(pendiente)",
      "Indica qué servicios hace cada profesional y su horario(pendiente)",
    ]);
  });

  it("adds the owner as a professional in one click", async () => {
    let body: unknown;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      ...emptyAgendaHandlers(),
      http.get("/api/v1/services", () => HttpResponse.json([])),
      http.get("/api/v1/staff", () => HttpResponse.json([])),
      http.post("/api/v1/staff", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...ana, displayName: "Ana Demo", userId: ownerMe.user.id }, { status: 201 });
      }),
    );

    renderWithQuery(<PanelHome />);
    await userEvent.click(await screen.findByRole("button", { name: "Añádete como profesional" }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith(`/panel/profesionales/${ana.id}`));
    expect(body).toEqual({ displayName: "Ana Demo", userId: ownerMe.user.id });
  });

  it("hides the checklist once everything is set up", async () => {
    const owner = { ...ana, userId: ownerMe.user.id };
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      ...emptyAgendaHandlers(),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.get("/api/v1/staff", () => HttpResponse.json([owner])),
    );

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText("No hay citas para hoy.")).toBeInTheDocument();
    await vi.waitFor(() => expect(screen.queryByText("Cargando…")).not.toBeInTheDocument());
    expect(screen.queryByText("Configura tu negocio")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Añádete como profesional" })).not.toBeInTheDocument();
  });

  it("does not offer setup actions to staff", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      ...emptyAgendaHandlers(),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.get("/api/v1/staff", () => HttpResponse.json([])),
    );

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText("Configura tu negocio")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Añádete como profesional" })).not.toBeInTheDocument();
  });

  it("shows today's appointments, without the cancelled ones", async () => {
    let query: URLSearchParams | undefined;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/business", () => HttpResponse.json(business)),
      http.get("/api/v1/appointments", ({ request }) => {
        query = new URL(request.url).searchParams;
        return HttpResponse.json(
          agenda({ appointments: [appointment(), appointment({ id: "e9", status: "CANCELLED" })] }),
        );
      }),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
    );

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText(/· 1 cita/)).toBeInTheDocument();
    const today = screen.getByRole("list", { name: "Citas de hoy" });
    expect(today).toHaveTextContent("Laura Pérez");
    expect(today).toHaveTextContent("Corte");
    expect(today.children).toHaveLength(1);
    expect(screen.getByRole("link", { name: "Abrir la agenda" })).toHaveAttribute("href", "/panel/agenda");
    expect(query?.get("from")).toBe(query?.get("to"));
  });
});
