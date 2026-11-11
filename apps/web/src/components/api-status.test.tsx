import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiStatus } from "./api-status";

describe("ApiStatus", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows that the server is connected when the ping succeeds", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      Response.json({ status: "ok", service: "slotea-api", time: "2026-01-15T10:00:00Z" }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(<ApiStatus />);

    expect(screen.getByRole("status")).toHaveTextContent("Conectando con el servidor…");
    expect(await screen.findByText(/Servidor conectado/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/ping", expect.anything());
  });

  it("shows an error when the API answers with an error status", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
    vi.spyOn(console, "error").mockImplementation(() => {});

    render(<ApiStatus />);

    expect(await screen.findByText("No se pudo conectar con el servidor.")).toBeInTheDocument();
  });
});
