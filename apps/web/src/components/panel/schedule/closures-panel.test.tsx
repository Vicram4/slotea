import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import type { Closure } from "@/lib/catalog";
import { ana, staffMe } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { ClosuresPanel } from "./closures-panel";

const christmas: Closure = {
  id: "0193a1b2-0000-7000-8000-00000000d001",
  staffId: null,
  startDate: "2026-12-24",
  endDate: "2026-12-26",
  reason: "Navidad",
  createdAt: "2026-11-01T10:00:00Z",
};

describe("ClosuresPanel", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("lists upcoming closures and absences with who they affect", async () => {
    let query: URLSearchParams | undefined;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
      http.get("/api/v1/closures", ({ request }) => {
        query = new URL(request.url).searchParams;
        return HttpResponse.json([
          christmas,
          { ...christmas, id: "d2", staffId: ana.id, startDate: "2027-08-01", endDate: "2027-08-01", reason: null },
        ]);
      }),
    );

    renderWithQuery(<ClosuresPanel />);

    expect(await screen.findByText("24 dic 2026 – 26 dic 2026")).toBeInTheDocument();
    expect(screen.getByText("Todo el negocio · Navidad")).toBeInTheDocument();
    expect(await within(screen.getByRole("list")).findByText("Ana")).toBeInTheDocument();
    expect(query?.get("from")).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(query?.has("staffId")).toBe(false);
  });

  it("creates a closure for one professional and refreshes the list", async () => {
    let body: unknown;
    let closures: Closure[] = [];
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
      http.get("/api/v1/closures", () => HttpResponse.json(closures)),
      http.post("/api/v1/closures", async ({ request }) => {
        body = await request.json();
        closures = [{ ...christmas, staffId: ana.id, reason: "Vacaciones" }];
        return HttpResponse.json(closures[0], { status: 201 });
      }),
    );
    renderWithQuery(<ClosuresPanel />);

    expect(await screen.findByText("No hay nada previsto.")).toBeInTheDocument();
    await screen.findByRole("option", { name: "Ana" });
    await userEvent.selectOptions(screen.getByLabelText("Afecta a"), "Ana");
    await userEvent.type(screen.getByLabelText("Desde"), "2026-12-24");
    await userEvent.type(screen.getByLabelText("Hasta (incluido)"), "2026-12-26");
    await userEvent.type(screen.getByLabelText("Motivo (opcional)"), "Vacaciones");
    await userEvent.click(screen.getByRole("button", { name: "Añadir" }));

    expect(await screen.findByText("Ana · Vacaciones")).toBeInTheDocument();
    expect(body).toEqual({ staffId: ana.id, startDate: "2026-12-24", endDate: "2026-12-26", reason: "Vacaciones" });
  });

  it("rejects an end date before the start date without calling the API", async () => {
    let called = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/staff", () => HttpResponse.json([])),
      http.get("/api/v1/closures", () => HttpResponse.json([])),
      http.post("/api/v1/closures", () => {
        called = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderWithQuery(<ClosuresPanel />);

    await userEvent.type(await screen.findByLabelText("Desde"), "2026-12-26");
    await userEvent.type(screen.getByLabelText("Hasta (incluido)"), "2026-12-24");
    await userEvent.click(screen.getByRole("button", { name: "Añadir" }));

    expect(await screen.findByText("Debe ser igual o posterior a la fecha de inicio.")).toBeInTheDocument();
    expect(called).toBe(false);
  });

  it("deletes a closure", async () => {
    let closures: Closure[] = [christmas];
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/staff", () => HttpResponse.json([])),
      http.get("/api/v1/closures", () => HttpResponse.json(closures)),
      http.delete(`/api/v1/closures/${christmas.id}`, () => {
        closures = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithQuery(<ClosuresPanel />);

    await userEvent.click(
      await screen.findByRole("button", { name: "Borrar cierre del 24 dic 2026 – 26 dic 2026" }),
    );

    expect(await screen.findByText("No hay nada previsto.")).toBeInTheDocument();
  });

  it("filters by professional and is read-only for staff", async () => {
    let query: URLSearchParams | undefined;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      http.get("/api/v1/staff", () => HttpResponse.json([ana])),
      http.get("/api/v1/closures", ({ request }) => {
        query = new URL(request.url).searchParams;
        return HttpResponse.json([{ ...christmas, staffId: ana.id }]);
      }),
    );

    renderWithQuery(<ClosuresPanel staffId={ana.id} />);

    const list = await screen.findByRole("list");
    expect(within(list).getByText("Navidad")).toBeInTheDocument();
    expect(query?.get("staffId")).toBe(ana.id);
    expect(screen.queryByRole("button", { name: "Añadir" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Borrar/ })).not.toBeInTheDocument();
  });
});
