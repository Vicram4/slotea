import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ana, corte, staffMe, tinte } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { StaffDetail } from "./staff-detail";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const barba = { ...corte, id: "0193a1b2-0000-7000-8000-00000000c003", name: "Barba" };

function serveStaff(me = ownerMe) {
  const calls: { method: string; body: unknown }[] = [];
  server.use(
    http.get("/api/v1/me", () => HttpResponse.json(me)),
    http.get(`/api/v1/staff/${ana.id}`, () => HttpResponse.json(ana)),
    http.get("/api/v1/services", () => HttpResponse.json([barba, corte, tinte])),
    http.get("/api/v1/business/members", () =>
      HttpResponse.json([
        { userId: ownerMe.user.id, email: ownerMe.user.email, fullName: "Ana Demo", role: "OWNER", joinedAt: "2026-11-01T10:00:00Z" },
      ]),
    ),
    http.put(`/api/v1/staff/${ana.id}`, async ({ request }) => {
      const body = (await request.json()) as Record<string, unknown>;
      calls.push({ method: "PUT staff", body });
      return HttpResponse.json({ ...ana, ...body });
    }),
    http.put(`/api/v1/staff/${ana.id}/services`, async ({ request }) => {
      const body = (await request.json()) as { serviceIds: string[] };
      calls.push({ method: "PUT services", body });
      return HttpResponse.json({ ...ana, serviceIds: body.serviceIds });
    }),
  );
  return calls;
}

describe("StaffDetail", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("edits the profile and links it to a member", async () => {
    const calls = serveStaff();
    renderWithQuery(<StaffDetail staffId={ana.id} />);

    expect(await screen.findByRole("heading", { name: "Ana" })).toBeInTheDocument();
    await screen.findByRole("option", { name: "Ana Demo (ana@example.test)" });
    await userEvent.selectOptions(screen.getByLabelText("Usuario del panel (opcional)"), ownerMe.user.id);
    await userEvent.click(screen.getByRole("switch", { name: "Acepta reservas" }));
    await userEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("Cambios guardados.")).toBeInTheDocument();
    expect(calls).toEqual([
      { method: "PUT staff", body: { displayName: "Ana", userId: ownerMe.user.id, active: false } },
    ]);
  });

  it("chooses the services the professional offers (inactive ones hidden unless assigned)", async () => {
    const calls = serveStaff();
    renderWithQuery(<StaffDetail staffId={ana.id} />);

    await userEvent.click(await screen.findByRole("tab", { name: "Servicios" }));
    expect(await screen.findByRole("checkbox", { name: /Corte/ })).toBeChecked();
    expect(screen.queryByRole("checkbox", { name: /Tinte/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("checkbox", { name: /Barba/ }));
    await userEvent.click(screen.getByRole("button", { name: "Guardar servicios" }));

    expect(await screen.findByText("Servicios guardados.")).toBeInTheDocument();
    expect(calls).toEqual([{ method: "PUT services", body: { serviceIds: [barba.id, corte.id] } }]);
  });

  it("says so when the professional does not exist (or belongs to another business)", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/staff/otro", () => HttpResponse.json({ status: 404, detail: "No existe." }, { status: 404 })),
    );

    renderWithQuery(<StaffDetail staffId="otro" />);

    expect(await screen.findByText("Este profesional no existe.")).toBeInTheDocument();
  });

  it("hides the profile tab and editing controls from staff", async () => {
    serveStaff(staffMe);
    renderWithQuery(<StaffDetail staffId={ana.id} />);

    expect(await screen.findByRole("heading", { name: "Ana" })).toBeInTheDocument();
    expect(screen.queryByRole("tab", { name: "Datos" })).not.toBeInTheDocument();
    expect(await screen.findByRole("checkbox", { name: /Corte/ })).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Guardar servicios" })).not.toBeInTheDocument();
  });
});
