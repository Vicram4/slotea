import { screen } from "@testing-library/react";
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
});
