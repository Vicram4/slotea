import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

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
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.get("/api/v1/staff", () => HttpResponse.json([])),
    );

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText("Hola, Ana Demo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /\/barberia-demo$/ })).toHaveAttribute("href", "/barberia-demo");
    expect(screen.getByText("Dueño")).toBeInTheDocument();
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

  it("does not offer setup actions to staff", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
    );

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText("Configura tu negocio")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Añádete como profesional" })).not.toBeInTheDocument();
  });
});
