import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import type { TimeSlot } from "@/lib/catalog";
import { ana, staffMe } from "@/test/catalog-fixtures";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { WeeklyHoursEditor } from "./weekly-hours-editor";

const HOURS = `/api/v1/staff/${ana.id}/working-hours`;

function serveHours(me = ownerMe, initial: TimeSlot[] = [{ dayOfWeek: 1, start: "09:00", end: "14:00" }]) {
  const saved: { slots: TimeSlot[] }[] = [];
  server.use(
    http.get("/api/v1/me", () => HttpResponse.json(me)),
    http.get(HOURS, () => HttpResponse.json({ slots: initial })),
    http.put(HOURS, async ({ request }) => {
      const body = (await request.json()) as { slots: TimeSlot[] };
      saved.push(body);
      return HttpResponse.json(body);
    }),
  );
  return saved;
}

describe("WeeklyHoursEditor", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("adds an afternoon slot (split shift) and saves the whole week", async () => {
    const saved = serveHours();
    renderWithQuery(<WeeklyHoursEditor staffId={ana.id} />);

    expect(await screen.findByLabelText("Lunes, tramo 1: inicio")).toHaveValue("09:00");
    await userEvent.click(screen.getByRole("button", { name: "Añadir tramo el lunes" }));
    expect(screen.getByLabelText("Lunes, tramo 2: inicio")).toHaveValue("16:00");
    await userEvent.click(screen.getByRole("button", { name: "Guardar horario" }));

    expect(await screen.findByText("Horario guardado.")).toBeInTheDocument();
    expect(saved).toEqual([
      {
        slots: [
          { dayOfWeek: 1, start: "09:00", end: "14:00" },
          { dayOfWeek: 1, start: "16:00", end: "20:00" },
        ],
      },
    ]);
  });

  it("copies Monday to the other weekdays", async () => {
    const saved = serveHours();
    renderWithQuery(<WeeklyHoursEditor staffId={ana.id} />);

    await userEvent.click(await screen.findByRole("button", { name: "Copiar el lunes de martes a viernes" }));
    expect(screen.getByLabelText("Viernes, tramo 1: fin")).toHaveValue("14:00");
    expect(screen.queryByLabelText("Sábado, tramo 1: inicio")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Guardar horario" }));

    await screen.findByText("Horario guardado.");
    expect(saved[0]!.slots.map((s) => s.dayOfWeek)).toEqual([1, 2, 3, 4, 5]);
  });

  it("does not save overlapping slots and says which one is wrong", async () => {
    const saved = serveHours();
    renderWithQuery(<WeeklyHoursEditor staffId={ana.id} />);

    await userEvent.click(await screen.findByRole("button", { name: "Añadir tramo el lunes" }));
    const start = screen.getByLabelText("Lunes, tramo 2: inicio");
    await userEvent.clear(start);
    await userEvent.type(start, "13:00");
    await userEvent.click(screen.getByRole("button", { name: "Guardar horario" }));

    expect(await screen.findByText("Se solapa con otro tramo del mismo día.")).toBeInTheDocument();
    expect(screen.getByLabelText("Lunes, tramo 2: inicio")).toHaveAttribute("aria-invalid", "true");
    expect(saved).toEqual([]);
  });

  it("removes slots", async () => {
    const saved = serveHours();
    renderWithQuery(<WeeklyHoursEditor staffId={ana.id} />);

    await userEvent.click(await screen.findByRole("button", { name: "Quitar Lunes, tramo 1" }));
    await userEvent.click(screen.getByRole("button", { name: "Guardar horario" }));

    await screen.findByText("Horario guardado.");
    expect(saved).toEqual([{ slots: [] }]);
  });

  it("shows a read-only summary to staff", async () => {
    serveHours(staffMe, [
      { dayOfWeek: 1, start: "09:00", end: "14:00" },
      { dayOfWeek: 1, start: "16:00", end: "20:00" },
    ]);
    renderWithQuery(<WeeklyHoursEditor staffId={ana.id} />);

    expect(await screen.findByText("09:00–14:00, 16:00–20:00")).toBeInTheDocument();
    expect(screen.getAllByText("Cerrado")).toHaveLength(6);
    expect(screen.queryByRole("button", { name: "Guardar horario" })).not.toBeInTheDocument();
  });
});
