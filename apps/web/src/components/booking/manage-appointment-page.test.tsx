import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Availability, ManagedAppointment, PublicBusiness } from "@/lib/booking";
import { addDays, todayIn } from "@/lib/dates";
import { server } from "@/test/msw";
import { renderWithQuery } from "@/test/render";

import { ManageAppointmentPage } from "./manage-appointment-page";

const SLUG = "barberia-demo";
const TOKEN = "tok_123";
const BASE = `/api/v1/public/businesses/${SLUG}`;
const MANAGE = `${BASE}/appointments/manage/${TOKEN}`;

const business: PublicBusiness = {
  slug: SLUG,
  name: "Barbería Demo",
  timezone: "Europe/Madrid",
  brandColor: "#1e3a8a",
  demo: false,
  services: [{ id: "s-corte", name: "Corte", description: null, durationMin: 30, priceCents: 1500 }],
  staff: [
    { id: "st-ana", displayName: "Ana", serviceIds: ["s-corte"] },
    { id: "st-luis", displayName: "Luis", serviceIds: ["s-corte"] },
  ],
};

const today = todayIn("Europe/Madrid");
const day = addDays(today, 3);

const confirmed: ManagedAppointment = {
  id: "a-1",
  status: "CONFIRMED",
  serviceId: "s-corte",
  serviceName: "Corte",
  staffId: "st-ana",
  staffName: "Ana",
  customerName: "Laura Pérez",
  startsAt: `${day}T09:00:00+01:00`,
  endsAt: `${day}T09:30:00+01:00`,
  date: day,
  time: "09:00",
  endTime: "09:30",
  timezone: "Europe/Madrid",
  changeable: true,
  changeableUntil: `${day}T07:00:00+01:00`,
};

/** Semana con huecos solo el primer día (09:15 y 10:00). */
function week(from: string): Availability {
  return {
    timezone: "Europe/Madrid",
    days: Array.from({ length: 7 }, (_, i) => {
      const date = addDays(from, i);
      return {
        date,
        slots: i === 0 ? ["09:15", "10:00"].map((time) => ({ time, startsAt: `${date}T${time}:00+01:00` })) : [],
      };
    }),
  };
}

let availabilityQueries: URLSearchParams[];
/** La cita «en el servidor»: los POST simulados la cambian, como la API real. */
let stored: ManagedAppointment | 404;

function appointmentIs(appointment: ManagedAppointment | 404) {
  availabilityQueries = [];
  stored = appointment;
  server.use(
    http.get(BASE, () => HttpResponse.json(business)),
    http.get(MANAGE, () =>
      stored === 404
        ? HttpResponse.json({ status: 404, detail: "Este enlace no es válido." }, { status: 404 })
        : HttpResponse.json(stored),
    ),
    http.get(`${MANAGE}/availability`, ({ request }) => {
      const params = new URL(request.url).searchParams;
      availabilityQueries.push(params);
      return HttpResponse.json(week(params.get("from")!));
    }),
  );
}

describe("ManageAppointmentPage", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("shows the appointment behind the link", async () => {
    appointmentIs(confirmed);
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    expect(await screen.findByRole("heading", { name: "Barbería Demo" })).toBeInTheDocument();
    expect(screen.getByText("Corte")).toBeInTheDocument();
    expect(screen.getByText("Confirmada")).toBeInTheDocument();
    expect(screen.getByText("Laura Pérez")).toBeInTheDocument();
    expect(screen.getByText("09:00 – 09:30")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cambiar hora" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancelar cita" })).toBeInTheDocument();
  });

  it("names the business time zone when the browser is in another one", async () => {
    const resolved = Intl.DateTimeFormat.prototype.resolvedOptions;
    vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions").mockImplementation(function (
      this: Intl.DateTimeFormat,
    ) {
      return { ...resolved.call(this), timeZone: "America/New_York" };
    });
    appointmentIs(confirmed);
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    expect(await screen.findByText("09:00 – 09:30 (Europe/Madrid)")).toBeInTheDocument();
  });

  it("explains an invalid link", async () => {
    appointmentIs(404);
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    expect(await screen.findByText(/Este enlace no es válido o la cita ya no existe/)).toBeInTheDocument();
  });

  it("cancels only after confirming, then offers to book again", async () => {
    appointmentIs(confirmed);
    let cancelled = 0;
    server.use(
      http.post(`${MANAGE}/cancel`, () => {
        cancelled++;
        stored = { ...confirmed, status: "CANCELLED", changeable: false };
        return HttpResponse.json(stored);
      }),
    );
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    await userEvent.click(await screen.findByRole("button", { name: "Cancelar cita" }));
    const dialog = await screen.findByRole("dialog", { name: "¿Cancelar la cita?" });
    await userEvent.click(within(dialog).getByRole("button", { name: "Mantener la cita" }));
    expect(cancelled).toBe(0);

    await userEvent.click(screen.getByRole("button", { name: "Cancelar cita" }));
    await userEvent.click(await screen.findByRole("button", { name: "Sí, cancelar" }));

    expect(await screen.findByText(/Tu cita está cancelada/)).toBeInTheDocument();
    expect(cancelled).toBe(1);
    expect(screen.getByText("Cancelada")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Reservar otra cita" })).toHaveAttribute("href", `/${SLUG}`);
    expect(screen.queryByRole("button", { name: "Cambiar hora" })).not.toBeInTheDocument();
  });

  it("offers to book again once the appointment is over", async () => {
    appointmentIs({ ...confirmed, status: "COMPLETED", changeable: false });
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    expect(await screen.findByRole("heading", { name: "Esta cita ya pasó" })).toBeInTheDocument();
    expect(screen.getByText("Completada")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Reservar otra cita" })).toHaveAttribute("href", `/${SLUG}`);
    expect(screen.queryByRole("button", { name: "Cambiar hora" })).not.toBeInTheDocument();
  });

  it("does not offer changes when it is too late", async () => {
    appointmentIs({ ...confirmed, changeable: false });
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    expect(await screen.findByText(/ya no se puede cambiar ni cancelar desde aquí/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar cita" })).not.toBeInTheDocument();
  });

  it("reschedules with the same professional by default", async () => {
    appointmentIs(confirmed);
    let body: unknown;
    server.use(
      http.post(`${MANAGE}/reschedule`, async ({ request }) => {
        body = await request.json();
        stored = { ...confirmed, time: "10:00", endTime: "10:30" };
        return HttpResponse.json(stored);
      }),
    );
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    await userEvent.click(await screen.findByRole("button", { name: "Cambiar hora" }));
    expect(screen.getByRole("radio", { name: "Ana" })).toBeChecked();
    await userEvent.click(await screen.findByRole("button", { name: "10:00" }));
    await userEvent.click(screen.getByRole("button", { name: /^Cambiar a:/ }));

    expect(await screen.findByText(/Hemos cambiado tu cita/)).toBeInTheDocument();
    expect(availabilityQueries[0]!.get("staffId")).toBe("st-ana");
    expect(availabilityQueries[0]!.get("from")).toBe(day);
    expect(body).toEqual({ startsAt: `${day}T10:00:00+01:00`, staffId: "st-ana" });
    expect(screen.getByText("10:00 – 10:30")).toBeInTheDocument();
  });

  it("when the new slot is taken meanwhile, says so and refreshes the slots", async () => {
    appointmentIs(confirmed);
    server.use(
      http.post(`${MANAGE}/reschedule`, () =>
        HttpResponse.json({ status: 409, detail: "Ese hueco acaba de ocuparse. Elige otra hora." }, { status: 409 }),
      ),
    );
    renderWithQuery(<ManageAppointmentPage slug={SLUG} token={TOKEN} />);

    await userEvent.click(await screen.findByRole("button", { name: "Cambiar hora" }));
    await userEvent.click(screen.getByRole("radio", { name: "Me da igual" }));
    await userEvent.click(await screen.findByRole("button", { name: "09:15" }));
    const before = availabilityQueries.length;
    await userEvent.click(screen.getByRole("button", { name: /^Cambiar a:/ }));

    expect(await screen.findByText("Ese hueco acaba de ocuparse. Elige otra hora.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Cambiar a:/ })).not.toBeInTheDocument();
    await waitFor(() => expect(availabilityQueries.length).toBeGreaterThan(before));
    expect(availabilityQueries.at(-1)!.has("staffId")).toBe(false); // «cualquier profesional»
  });
});
