import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { LoginForm } from "./login-form";

const router = { push: vi.fn(), replace: vi.fn() };
vi.mock("next/navigation", () => ({ useRouter: () => router }));

describe("LoginForm", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("validates the fields before calling the API", async () => {
    renderWithQuery(<LoginForm />);

    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByText("Introduce un email válido.")).toBeInTheDocument();
    expect(screen.getByText("Introduce tu contraseña.")).toBeInTheDocument();
    expect(screen.getByLabelText("Email")).toHaveAttribute("aria-invalid", "true");
  });

  it("logs in, stores the session and goes to the panel", async () => {
    let body: unknown;
    server.use(
      http.post("/api/v1/auth/login", async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(ownerMe);
      }),
    );
    const { queryClient } = renderWithQuery(<LoginForm />);

    await userEvent.type(screen.getByLabelText("Email"), "ana@example.test");
    await userEvent.type(screen.getByLabelText("Contraseña"), "correct-horse-battery");
    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));

    await vi.waitFor(() => expect(router.push).toHaveBeenCalledWith("/panel"));
    expect(body).toEqual({ email: "ana@example.test", password: "correct-horse-battery" });
    expect(queryClient.getQueryData(["me"])).toEqual(ownerMe);
  });

  it("shows the API message when the credentials are wrong", async () => {
    server.use(
      http.post("/api/v1/auth/login", () =>
        HttpResponse.json({ status: 401, detail: "Email o contraseña incorrectos." }, { status: 401 }),
      ),
    );
    renderWithQuery(<LoginForm />);

    await userEvent.type(screen.getByLabelText("Email"), "ana@example.test");
    await userEvent.type(screen.getByLabelText("Contraseña"), "wrong-password");
    await userEvent.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByText("Email o contraseña incorrectos.")).toBeInTheDocument();
    expect(router.push).not.toHaveBeenCalled();
  });
});
