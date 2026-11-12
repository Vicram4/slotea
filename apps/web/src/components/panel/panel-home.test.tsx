import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { PanelHome } from "./panel-home";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

describe("PanelHome", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("shows who I am: user, business and role", async () => {
    server.use(http.get("/api/v1/me", () => HttpResponse.json(ownerMe)));

    renderWithQuery(<PanelHome />);

    expect(await screen.findByText("Hola, Ana Demo")).toBeInTheDocument();
    expect(screen.getByText("Barbería Demo")).toBeInTheDocument();
    expect(screen.getByText("/barberia-demo")).toBeInTheDocument();
    expect(screen.getByText("Dueño")).toBeInTheDocument();
  });

  it("goes to the login page when there is no session", async () => {
    server.use(
      http.get("/api/v1/me", () => new HttpResponse(null, { status: 401 })),
      http.post("/api/v1/auth/refresh", () => new HttpResponse(null, { status: 401 })),
    );

    renderWithQuery(<PanelHome />);

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
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
    const { queryClient } = renderWithQuery(<PanelHome />);

    await userEvent.click(await screen.findByRole("button", { name: "Cerrar sesión" }));

    await vi.waitFor(() => expect(router.replace).toHaveBeenCalledWith("/login"));
    expect(loggedOut).toBe(true);
    expect(queryClient.getQueryData(["me"])).toBeNull();
  });
});
