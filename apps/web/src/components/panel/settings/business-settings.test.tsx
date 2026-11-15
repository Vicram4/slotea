import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { business } from "@/test/agenda-fixtures";
import { staffMe } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { BusinessSettings } from "./business-settings";

describe("BusinessSettings", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  function ownerWithBusiness() {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/business", () => HttpResponse.json(business)),
    );
  }

  it("saves the details and a suggested brand color", async () => {
    ownerWithBusiness();
    let body: unknown;
    server.use(
      http.patch("/api/v1/business", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ ...business, ...(body as object) });
      }),
    );
    renderWithQuery(<BusinessSettings />);

    const name = await screen.findByLabelText("Nombre");
    expect(name).toHaveValue("Barbería Demo");
    expect(screen.getByLabelText("Zona horaria")).toHaveValue("Europe/Madrid");
    expect(screen.getByRole("button", { name: "Grafito" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.clear(name);
    await userEvent.type(name, "Barbería Ejemplo");
    await userEvent.click(screen.getByRole("button", { name: "Verde azulado" }));
    expect(screen.getByLabelText("Código del color")).toHaveValue("#0f766e");
    expect(screen.getByText(/Contraste con el blanco: 5,5:1/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("Cambios guardados.")).toBeInTheDocument();
    expect(body).toEqual({
      name: "Barbería Ejemplo",
      contactEmail: business.contactEmail,
      timezone: "Europe/Madrid",
      brandColor: "#0f766e",
    });
    expect(screen.getByRole("button", { name: "Verde azulado" })).toHaveAttribute("aria-pressed", "true");
  });

  it("does not send a color that would not read well on white", async () => {
    ownerWithBusiness();
    let patched = false;
    server.use(
      http.patch("/api/v1/business", () => {
        patched = true;
        return HttpResponse.json(business);
      }),
    );
    renderWithQuery(<BusinessSettings />);

    const code = await screen.findByLabelText("Código del color");
    await userEvent.clear(code);
    await userEvent.type(code, "#ffd700");
    await userEvent.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText(/demasiado claro/)).toBeInTheDocument();
    expect(code).toHaveAttribute("aria-invalid", "true");
    expect(patched).toBe(false);
  });

  it("shows the API's field errors next to the field", async () => {
    ownerWithBusiness();
    server.use(
      http.patch("/api/v1/business", () =>
        HttpResponse.json(
          { status: 400, detail: "Datos no válidos.", errors: [{ field: "contactEmail", message: "no es válido" }] },
          { status: 400 },
        ),
      ),
    );
    renderWithQuery(<BusinessSettings />);

    await userEvent.click(await screen.findByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("no es válido")).toBeInTheDocument();
    expect(screen.getByLabelText("Email de contacto")).toHaveAttribute("aria-invalid", "true");
  });

  it("tells an employee that only the owner can change the settings", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      http.get("/api/v1/business", () => HttpResponse.json(business)),
    );
    renderWithQuery(<BusinessSettings />);

    expect(await screen.findByText("Solo el dueño del negocio puede cambiar los ajustes.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Guardar cambios" })).not.toBeInTheDocument();
  });
});
