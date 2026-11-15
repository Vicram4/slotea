import { screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ana, corte, tinte } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { StaffListPage } from "./staff-list-page";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const luis = {
  ...ana,
  id: "0193a1b2-0000-7000-8000-00000000a002",
  displayName: "Luis",
  userId: "0193a1b2-0000-7000-8000-000000000002",
  serviceIds: [],
};
const marta = { ...ana, id: "0193a1b2-0000-7000-8000-00000000a003", displayName: "Marta", active: false };

describe("StaffListPage", () => {
  beforeEach(() => {
    server.use(
      http.get("/api/v1/me", () => HttpResponse.json(ownerMe)),
      http.get("/api/v1/services", () => HttpResponse.json([corte, tinte])),
      http.get("/api/v1/staff", () => HttpResponse.json([ana, luis, marta])),
    );
  });

  it("lists the team with their services and what needs attention", async () => {
    renderWithQuery(<StaffListPage />);

    const anaLink = await screen.findByRole("link", { name: /^Ana/ });
    expect(anaLink).toHaveAttribute("href", `/panel/profesionales/${ana.id}`);
    expect(within(anaLink).getByText("Corte")).toBeInTheDocument();

    const luisLink = screen.getByRole("link", { name: /^Luis/ });
    expect(within(luisLink).getByText("Sin servicios: todavía no se le puede reservar")).toBeInTheDocument();
    expect(within(luisLink).getByText("Entra en el panel")).toBeInTheDocument();

    expect(within(screen.getByRole("link", { name: /^Marta/ })).getByText("No acepta reservas")).toBeInTheDocument();
    expect(screen.getByText(/^3 en el equipo \(1 sin aceptar reservas\)/)).toBeInTheDocument();
  });
});
