import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { SignupForm } from "./signup-form";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

async function fillForm() {
  await userEvent.type(screen.getByLabelText("Nombre del negocio"), "Barbería Demo");
  await userEvent.type(screen.getByLabelText("Tu nombre"), "Ana Demo");
  await userEvent.type(screen.getByLabelText("Email"), "ana@example.test");
  await userEvent.type(screen.getByLabelText("Contraseña"), "correct-horse-battery");
}

describe("SignupForm", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("suggests the public address from the business name until the user edits it", async () => {
    renderWithQuery(<SignupForm />);
    const slug = screen.getByLabelText("Dirección de tu página de reservas");

    await userEvent.type(screen.getByLabelText("Nombre del negocio"), "Peluquería Ana");
    expect(slug).toHaveValue("peluqueria-ana");
    expect(screen.getByText("Tu página será /peluqueria-ana")).toBeInTheDocument();

    await userEvent.clear(slug);
    await userEvent.type(slug, "ana");
    await userEvent.type(screen.getByLabelText("Nombre del negocio"), " Centro");
    expect(slug).toHaveValue("ana");
  });

  it("creates the business and goes to the panel", async () => {
    let body: Record<string, unknown> = {};
    server.use(
      http.post("/api/v1/auth/signup", async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(ownerMe, { status: 201 });
      }),
    );
    renderWithQuery(<SignupForm />);

    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/panel"));
    expect(body).toMatchObject({
      businessName: "Barbería Demo",
      slug: "barberia-demo",
      fullName: "Ana Demo",
      email: "ana@example.test",
    });
  });

  it("shows a taken address (409) as a general message", async () => {
    server.use(
      http.post("/api/v1/auth/signup", () =>
        HttpResponse.json(
          { status: 409, detail: "Esa dirección ya está en uso. Prueba con otra." },
          { status: 409 },
        ),
      ),
    );
    renderWithQuery(<SignupForm />);

    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

    expect(await screen.findByText("Esa dirección ya está en uso. Prueba con otra.")).toBeInTheDocument();
  });

  it("puts the API validation errors next to each field", async () => {
    server.use(
      http.post("/api/v1/auth/signup", () =>
        HttpResponse.json(
          {
            status: 400,
            detail: "La petición tiene datos no válidos.",
            errors: [{ field: "email", message: "el email ya no es válido" }],
          },
          { status: 400 },
        ),
      ),
    );
    renderWithQuery(<SignupForm />);

    await fillForm();
    await userEvent.click(screen.getByRole("button", { name: "Crear cuenta" }));

    expect(await screen.findByText("el email ya no es válido")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
    expect(screen.queryByText("La petición tiene datos no válidos.")).not.toBeInTheDocument();
  });
});
