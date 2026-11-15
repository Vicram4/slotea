import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { PanelShell } from "./panel-shell";

const router = { push: vi.fn(), replace: vi.fn() };
let pathname = "/panel/servicios";
vi.mock("next/navigation", () => ({ useRouter: () => router, usePathname: () => pathname }));

describe("PanelShell", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    pathname = "/panel/servicios";
  });

  it("shows the business, the navigation and the page once the session is loaded", async () => {
    server.use(http.get("/api/v1/me", () => HttpResponse.json(ownerMe)));

    renderWithQuery(
      <PanelShell>
        <p>Contenido</p>
      </PanelShell>,
    );

    expect(await screen.findByText("Contenido")).toBeInTheDocument();
    expect(screen.getByText("Barbería Demo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Servicios" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Inicio" })).not.toHaveAttribute("aria-current");
  });

  it("goes to the login page when there is no session", async () => {
    server.use(
      http.get("/api/v1/me", () => new HttpResponse(null, { status: 401 })),
      http.post("/api/v1/auth/refresh", () => new HttpResponse(null, { status: 401 })),
    );

    renderWithQuery(<PanelShell>Contenido</PanelShell>);

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByText("Contenido")).not.toBeInTheDocument();
  });

  it("logs out and goes to the login page", async () => {
    let loggedOut = false;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.post("/api/v1/auth/logout", () => {
        loggedOut = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { queryClient } = renderWithQuery(<PanelShell>Contenido</PanelShell>);

    await userEvent.click(await screen.findByRole("button", { name: "Cerrar sesión" }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(loggedOut).toBe(true);
    expect(queryClient.getQueryData(["me"])).toBeNull();
  });

  it("shows no demo bar for a real business", async () => {
    server.use(http.get("/api/v1/me", () => HttpResponse.json(ownerMe)));
    renderWithQuery(<PanelShell>Contenido</PanelShell>);

    expect(await screen.findByText("Contenido")).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Demo" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ajustes" })).toHaveAttribute("href", "/panel/ajustes");
    expect(screen.queryByRole("link", { name: "Bandeja de la demo" })).not.toBeInTheDocument();
  });

  it("in a demo copy, offers the inbox, the public page and switching to the employee", async () => {
    const business = { ...ownerMe.activeBusiness!, slug: "demo-abc12345", demo: true, demoCanSwitch: true };
    const demoOwner = { ...ownerMe, activeBusiness: business, memberships: [business] };
    const asStaff = { ...demoOwner, activeBusiness: { ...business, role: "STAFF" as const } };
    let switchedTo: unknown;
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(demoOwner)),
      http.post("/api/v1/demo/switch", async ({ request }) => {
        switchedTo = await request.json();
        return HttpResponse.json(asStaff);
      }),
    );
    const { queryClient } = renderWithQuery(<PanelShell>Contenido</PanelShell>);

    const bar = await screen.findByRole("region", { name: "Demo" });
    expect(within(bar).getByRole("link", { name: "Reservar como cliente" })).toHaveAttribute("href", "/demo-abc12345");
    expect(screen.getAllByRole("link", { name: "Bandeja de la demo" })).toHaveLength(2); // barra y navegación

    await userEvent.click(within(bar).getByRole("button", { name: "Ver como empleado" }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/panel/agenda"));
    expect(switchedTo).toEqual({ role: "STAFF" });
    expect(queryClient.getQueryData<typeof asStaff>(["me"])?.activeBusiness?.role).toBe("STAFF");
    // El empleado no tiene bandeja: los emails van al negocio y a los clientes.
    expect(await within(bar).findByRole("button", { name: "Ver como dueño" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Bandeja de la demo" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Ajustes" })).not.toBeInTheDocument();
  });

  it("in a demo copy without an employee (psychology), does not offer switching", async () => {
    const business = { ...ownerMe.activeBusiness!, slug: "demo-abc12345", demo: true, demoCanSwitch: false };
    const demoOwner = { ...ownerMe, activeBusiness: business, memberships: [business] };
    server.use(http.get("/api/v1/me", () => HttpResponse.json(demoOwner)));
    renderWithQuery(<PanelShell>Contenido</PanelShell>);

    const bar = await screen.findByRole("region", { name: "Demo" });
    expect(within(bar).getByRole("link", { name: "Reservar como cliente" })).toBeInTheDocument();
    expect(within(bar).queryByRole("button", { name: /Ver como/ })).not.toBeInTheDocument();
  });
});
