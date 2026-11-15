import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Me } from "@/lib/api/client";
import type { DemoSector } from "@/lib/demo";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { DemoLauncher } from "./demo-launcher";
import { DEMO_SECTORS } from "./demo-sectors";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

function demoMe(role: "OWNER" | "STAFF"): Me {
  const business = { ...ownerMe.activeBusiness!, slug: "demo-abc12345", role, demo: true, demoCanSwitch: true };
  return { ...ownerMe, activeBusiness: business, memberships: [business] };
}

type Requested = { sector: DemoSector; role: "OWNER" | "STAFF" };

/** Responde a la creación de la copia y guarda lo que se ha pedido. */
function demoApi() {
  const requested: Requested[] = [];
  server.use(
    http.post("/api/v1/demo/sessions", async ({ request }) => {
      const body = (await request.json()) as Requested;
      requested.push(body);
      return HttpResponse.json(demoMe(body.role), { status: 201 });
    }),
  );
  return requested;
}

async function chooseSector(name: string) {
  await userEvent.click(screen.getByRole("button", { name: new RegExp(`^${name}`) }));
}

describe("DemoLauncher", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    router.push.mockReset();
  });

  it("first asks for a sector, with all six of them", () => {
    renderWithQuery(<DemoLauncher />);

    for (const { name } of Object.values(DEMO_SECTORS)) {
      expect(screen.getByRole("button", { name: new RegExp(`^${name}`) })).toBeInTheDocument();
    }
    expect(screen.queryByRole("button", { name: /Entrar como negocio/ })).not.toBeInTheDocument();
  });

  it.each([
    ["Entrar como negocio", "OWNER", "/panel"],
    ["Entrar como empleado", "STAFF", "/panel/agenda"],
    ["Reservar como cliente", "OWNER", "/demo-abc12345"],
  ])("«%s» creates a copy of the chosen sector as %s and goes to %s", async (label, role, target) => {
    const requested = demoApi();
    const { queryClient } = renderWithQuery(<DemoLauncher />);

    await chooseSector("Taller mecánico");
    await userEvent.click(screen.getByRole("button", { name: new RegExp(label) }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith(target));
    expect(requested).toEqual([{ sector: "MECHANIC", role }]);
    expect(queryClient.getQueryData<Me>(["me"])?.activeBusiness?.demo).toBe(true);
  });

  it("does not offer the employee view in a single-professional sector", async () => {
    const requested = demoApi();
    renderWithQuery(<DemoLauncher />);

    await chooseSector("Psicología");

    expect(screen.getByText(/una sola profesional: no tiene vista de empleado/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Entrar como empleado/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /Entrar como negocio/ }));
    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/panel"));
    expect(requested).toEqual([{ sector: "PSYCHOLOGY", role: "OWNER" }]);
  });

  it("moves the focus between steps and can go back to another sector", async () => {
    renderWithQuery(<DemoLauncher />);

    await chooseSector("Veterinaria");
    expect(screen.getByRole("heading", { name: /Veterinaria: ¿cómo quieres entrar\?/ })).toHaveFocus();

    await userEvent.click(screen.getByRole("button", { name: "Otro sector" }));
    expect(screen.getByRole("button", { name: /^Veterinaria/ })).toHaveFocus();
    expect(screen.queryByRole("button", { name: /Entrar como negocio/ })).not.toBeInTheDocument();
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

    await chooseSector("Barbería");
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

    await chooseSector("Fisioterapia");
    await userEvent.click(screen.getByRole("button", { name: /Entrar como empleado/ }));

    expect(await screen.findByText("La demo está llena ahora mismo. Prueba de nuevo en un rato.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Entrar como empleado/ })).toBeEnabled();
    expect(router.push).not.toHaveBeenCalled();

    // Al cambiar de sector, el error de antes desaparece.
    await userEvent.click(screen.getByRole("button", { name: "Otro sector" }));
    expect(screen.queryByText(/La demo está llena/)).not.toBeInTheDocument();
  });

  it("explains when there have been too many demos in a row", async () => {
    const detail = "Demasiadas peticiones seguidas. Espera un momento y vuelve a intentarlo.";
    server.use(
      http.post("/api/v1/demo/sessions", () =>
        HttpResponse.json({ status: 429, detail }, { status: 429, headers: { "Retry-After": "180" } }),
      ),
    );
    renderWithQuery(<DemoLauncher />);

    await chooseSector("Peluquería y estética");
    await userEvent.click(screen.getByRole("button", { name: /Entrar como negocio/ }));

    expect(await screen.findByText(detail)).toBeInTheDocument();
  });
});
