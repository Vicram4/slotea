import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Me } from "@/lib/api/client";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { DemoLauncher } from "./demo-launcher";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

function demoMe(role: "OWNER" | "STAFF"): Me {
  const business = { ...ownerMe.activeBusiness!, slug: "demo-abc12345", role, demo: true };
  return { ...ownerMe, activeBusiness: business, memberships: [business] };
}

/** Responde a la creación de la copia y guarda el rol pedido. */
function demoApi() {
  const requested: string[] = [];
  server.use(
    http.post("/api/v1/demo/sessions", async ({ request }) => {
      const { role } = (await request.json()) as { role: "OWNER" | "STAFF" };
      requested.push(role);
      return HttpResponse.json(demoMe(role), { status: 201 });
    }),
  );
  return requested;
}

describe("DemoLauncher", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    router.push.mockReset();
  });

  it.each([
    ["Entrar como negocio", "OWNER", "/panel"],
    ["Entrar como empleado", "STAFF", "/panel/agenda"],
    ["Reservar como cliente", "OWNER", "/demo-abc12345"],
  ])("«%s» creates a copy as %s and goes to %s", async (label, role, target) => {
    const requested = demoApi();
    const { queryClient } = renderWithQuery(<DemoLauncher />);

    await userEvent.click(screen.getByRole("button", { name: new RegExp(label) }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith(target));
    expect(requested).toEqual([role]);
    expect(queryClient.getQueryData<Me>(["me"])?.activeBusiness?.demo).toBe(true);
  });

  it("says the server is waking up when it takes a while", async () => {
    let release!: () => void;
    const released = new Promise<void>((resolve) => (release = resolve));
    server.use(
      http.post("/api/v1/demo/sessions", async () => {
        await released;
        return HttpResponse.json(demoMe("OWNER"), { status: 201 });
      }),
    );
    renderWithQuery(<DemoLauncher wakeUpAfterMs={10} />);

    await userEvent.click(screen.getByRole("button", { name: /Entrar como negocio/ }));

    expect(await screen.findByText(/Despertando el servidor/)).toBeInTheDocument();
    for (const button of screen.getAllByRole("button")) expect(button).toBeDisabled();
    release();
    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/panel"));
  });

  it("shows the API's explanation when the demo is full", async () => {
    server.use(
      http.post("/api/v1/demo/sessions", () =>
        HttpResponse.json(
          { status: 503, detail: "La demo está llena ahora mismo. Prueba de nuevo en un rato." },
          { status: 503 },
        ),
      ),
    );
    renderWithQuery(<DemoLauncher />);

    await userEvent.click(screen.getByRole("button", { name: /Entrar como empleado/ }));

    expect(await screen.findByText("La demo está llena ahora mismo. Prueba de nuevo en un rato.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Entrar como empleado/ })).toBeEnabled();
    expect(router.push).not.toHaveBeenCalled();
  });

  it("explains when there have been too many demos in a row", async () => {
    const detail = "Demasiadas peticiones seguidas. Espera un momento y vuelve a intentarlo.";
    server.use(
      http.post("/api/v1/demo/sessions", () =>
        HttpResponse.json({ status: 429, detail }, { status: 429, headers: { "Retry-After": "180" } }),
      ),
    );
    renderWithQuery(<DemoLauncher />);

    await userEvent.click(screen.getByRole("button", { name: /Entrar como negocio/ }));

    expect(await screen.findByText(detail)).toBeInTheDocument();
  });
});
