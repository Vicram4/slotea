import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { corte, staffMe, tinte } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { ServicesPage } from "./services-page";

describe("ServicesPage", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("lists services with duration, price and status", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/services", () => HttpResponse.json([corte, tinte])),
    );

    renderWithQuery(<ServicesPage />);

    const row = await screen.findByRole("row", { name: /^Corte/ });
    expect(within(row).getByText("30 min")).toBeInTheDocument();
    expect(within(row).getByText("15,00 €")).toBeInTheDocument();
    expect(within(row).getByText("Activo")).toBeInTheDocument();
    const inactive = screen.getByRole("row", { name: /^Tinte/ });
    expect(within(inactive).getByText("1 h 30 min")).toBeInTheDocument();
    expect(within(inactive).getByText("Desactivado")).toBeInTheDocument();
  });

  it("creates a service from the dialog", async () => {
    let body: unknown;
    let services = [corte];
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/services", () => HttpResponse.json(services)),
      http.post("/api/v1/services", async ({ request }) => {
        body = await request.json();
        const created = { ...corte, id: "new", name: "Barba", description: null, priceCents: 850 };
        services = [...services, created];
        return HttpResponse.json(created, { status: 201 });
      }),
    );
    renderWithQuery(<ServicesPage />);

    await userEvent.click(await screen.findByRole("button", { name: "Nuevo servicio" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText("Nombre"), "Barba");
    await userEvent.clear(within(dialog).getByLabelText("Duración (min)"));
    await userEvent.type(within(dialog).getByLabelText("Duración (min)"), "20");
    await userEvent.type(within(dialog).getByLabelText("Precio (€, opcional)"), "8,50");
    await userEvent.click(within(dialog).getByRole("button", { name: "Guardar servicio" }));

    expect(await screen.findByRole("row", { name: /^Barba/ })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(body).toEqual({
      name: "Barba",
      description: null,
      durationMin: 20,
      bufferMin: 0,
      priceCents: 850,
      active: true,
    });
  });

  it("shows the API error when the name is already taken", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
      http.put(`/api/v1/services/${corte.id}`, () =>
        HttpResponse.json({ status: 409, detail: "Ya tienes un servicio con ese nombre." }, { status: 409 }),
      ),
    );
    renderWithQuery(<ServicesPage />);

    await userEvent.click(await screen.findByRole("button", { name: "Editar Corte" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText("Precio (€, opcional)")).toHaveValue("15");
    await userEvent.click(within(dialog).getByRole("button", { name: "Guardar servicio" }));

    expect(await within(dialog).findByText("Ya tienes un servicio con ese nombre.")).toBeInTheDocument();
  });

  it("maps the API price error to the price field", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/services", () => HttpResponse.json([])),
      http.post("/api/v1/services", () =>
        HttpResponse.json(
          { status: 400, detail: "La petición tiene datos no válidos.", errors: [{ field: "priceCents", message: "demasiado alto" }] },
          { status: 400 },
        ),
      ),
    );
    renderWithQuery(<ServicesPage />);

    await userEvent.click(await screen.findByRole("button", { name: "Nuevo servicio" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.type(within(dialog).getByLabelText("Nombre"), "Caro");
    await userEvent.click(within(dialog).getByRole("button", { name: "Guardar servicio" }));

    expect(await within(dialog).findByText("demasiado alto")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Precio (€, opcional)")).toHaveAttribute("aria-invalid", "true");
  });

  it("is read-only for staff", async () => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(staffMe)),
      http.get("/api/v1/services", () => HttpResponse.json([corte])),
    );

    renderWithQuery(<ServicesPage />);

    expect(await screen.findByText("Corte")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Nuevo servicio" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Editar/ })).not.toBeInTheDocument();
  });
});
