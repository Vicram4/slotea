import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import type { Availability, Booking, PublicBusiness } from "@/lib/booking";
import { addDays, todayIn } from "@/lib/dates";
import { server } from "@/test/msw";
import { renderWithQuery } from "@/test/render";

import { BookingPage } from "./booking-page";

const SLUG = "barberia-demo";
const BASE = `/api/v1/public/businesses/${SLUG}`;

const business: PublicBusiness = {
  slug: SLUG,
  name: "Barbería Demo",
  timezone: "Europe/Madrid",
  services: [
    { id: "s-corte", name: "Corte", description: "Lavado incluido", durationMin: 30, priceCents: 1500 },
    { id: "s-barba", name: "Barba", description: null, durationMin: 20, priceCents: null },
  ],
  staff: [
    { id: "st-ana", displayName: "Ana", serviceIds: ["s-corte", "s-barba"] },
    { id: "st-luis", displayName: "Luis", serviceIds: ["s-barba"] },
  ],
};

const today = todayIn("Europe/Madrid");

/** Semana pedida con huecos solo el segundo día (09:00, 09:15 y 16:00). */
function week(from: string, times = ["09:00", "09:15", "16:00"]): Availability {
  return {
    timezone: "Europe/Madrid",
    days: Array.from({ length: 7 }, (_, i) => {
      const date = addDays(from, i);
      return {
        date,
        slots: i === 1 ? times.map((time) => ({ time, startsAt: `${date}T${time}:00+01:00` })) : [],
      };
    }),
  };
}

function confirmation(startsAt: string, staffName = "Ana"): Booking {
  return {
    id: "b-1",
    serviceName: "Corte",
    staffName,
    startsAt,
    endsAt: startsAt,
    date: addDays(today, 1),
    time: "16:00",
    timezone: "Europe/Madrid",
    customerEmail: "laura@example.test",
  };
}

/** Peticiones de disponibilidad recibidas (sus parámetros). */
let availabilityQueries: URLSearchParams[];

function businessWithAvailability(times?: string[]) {
  availabilityQueries = [];
  server.use(
    http.get(BASE, () => HttpResponse.json(business)),
    http.get(`${BASE}/availability`, ({ request }) => {
      const params = new URL(request.url).searchParams;
      availabilityQueries.push(params);
      return HttpResponse.json(week(params.get("from")!, times));
    }),
  );
}

async function chooseCorteAt(time: string) {
  await userEvent.click(await screen.findByRole("radio", { name: /Corte/ }));
  await userEvent.click(await screen.findByRole("button", { name: time }));
}

async function fillCustomer() {
  await userEvent.type(screen.getByLabelText("Nombre"), "Laura Pérez");
  await userEvent.type(screen.getByLabelText("Email"), "laura@example.test");
}

describe("BookingPage", () => {
  beforeEach(() => {
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  it("books a slot as a guest from service to confirmation", async () => {
    businessWithAvailability();
    let body: unknown;
    server.use(
      http.post(`${BASE}/appointments`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(confirmation((body as { startsAt: string }).startsAt), { status: 201 });
      }),
    );
    renderWithQuery(<BookingPage slug={SLUG} />);

    expect(await screen.findByRole("heading", { name: "Barbería Demo" })).toBeInTheDocument();
    expect(screen.getByText("30 min · 15,00 €")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("radio", { name: /Corte/ }));

    // Solo los profesionales que hacen el corte, más «cualquiera».
    const staff = screen.getByLabelText("Profesional");
    expect(within(staff).getAllByRole("option").map((o) => o.textContent)).toEqual(["Cualquier profesional", "Ana"]);

    // Primera semana: desde hoy (en Madrid). El primer día con huecos se elige solo.
    expect(await screen.findByRole("group", { name: "Tarde" })).toBeInTheDocument();
    expect(availabilityQueries[0]!.get("from")).toBe(today);
    expect(availabilityQueries[0]!.get("to")).toBe(addDays(today, 6));
    expect(availabilityQueries[0]!.has("staffId")).toBe(false);
    expect(screen.getByRole("button", { name: /Semana anterior/ })).toBeDisabled();

    await userEvent.click(within(screen.getByRole("group", { name: "Tarde" })).getByRole("button", { name: "16:00" }));
    expect(screen.getByText(/Corte con cualquier profesional/)).toBeInTheDocument();
    await fillCustomer();
    await userEvent.type(screen.getByLabelText("Teléfono (opcional)"), "600 000 000");
    await userEvent.click(screen.getByRole("button", { name: "Confirmar reserva" }));

    expect(await screen.findByText("¡Reserva confirmada!")).toBeInTheDocument();
    expect(body).toEqual({
      serviceId: "s-corte",
      staffId: null,
      startsAt: `${addDays(today, 1)}T16:00:00+01:00`,
      customer: { fullName: "Laura Pérez", email: "laura@example.test", phone: "600 000 000" },
      notes: null,
    });
  });

  it("asks for availability of the chosen professional and the next week", async () => {
    businessWithAvailability();
    renderWithQuery(<BookingPage slug={SLUG} />);

    await userEvent.click(await screen.findByRole("radio", { name: /Barba/ }));
    await userEvent.selectOptions(screen.getByLabelText("Profesional"), "Luis");
    await screen.findByRole("group", { name: "Mañana" });
    expect(availabilityQueries.at(-1)!.get("staffId")).toBe("st-luis");

    await userEvent.click(screen.getByRole("button", { name: "Semana siguiente" }));
    await waitFor(() => expect(availabilityQueries.at(-1)!.get("from")).toBe(addDays(today, 7)));
    expect(await screen.findByRole("button", { name: "Semana anterior" })).toBeEnabled();
  });

  it("days without slots are disabled", async () => {
    businessWithAvailability();
    renderWithQuery(<BookingPage slug={SLUG} />);
    await userEvent.click(await screen.findByRole("radio", { name: /Corte/ }));
    await screen.findByRole("group", { name: "Mañana" });

    const dayButtons = screen.getAllByRole("button", { pressed: false }).filter((b) => b.getAttribute("aria-label"));
    expect(dayButtons.filter((b) => b.getAttribute("aria-label")!.includes("sin huecos"))).toHaveLength(6);
    for (const button of dayButtons.filter((b) => b.getAttribute("aria-label")!.includes("sin huecos"))) {
      expect(button).toBeDisabled();
    }
  });

  it("when the slot is taken meanwhile, explains it, refreshes the slots and keeps what was typed", async () => {
    businessWithAvailability();
    server.use(
      http.post(`${BASE}/appointments`, () =>
        HttpResponse.json(
          { status: 409, detail: "Ese hueco acaba de ocuparse. Elige otra hora." },
          { status: 409 },
        ),
      ),
    );
    renderWithQuery(<BookingPage slug={SLUG} />);
    await chooseCorteAt("09:00");
    await fillCustomer();
    const requestsBefore = availabilityQueries.length;

    await userEvent.click(screen.getByRole("button", { name: "Confirmar reserva" }));

    expect(await screen.findByText("Ese hueco acaba de ocuparse. Elige otra hora.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Nombre")).not.toBeInTheDocument(); // hay que elegir otra hora
    await waitFor(() => expect(availabilityQueries.length).toBeGreaterThan(requestsBefore));

    await userEvent.click(screen.getByRole("button", { name: "09:15" }));
    expect(screen.getByLabelText("Nombre")).toHaveValue("Laura Pérez");
    expect(screen.queryByText("Ese hueco acaba de ocuparse. Elige otra hora.")).not.toBeInTheDocument();
  });

  it("validates the guest data before calling the API", async () => {
    businessWithAvailability();
    let posted = false;
    server.use(
      http.post(`${BASE}/appointments`, () => {
        posted = true;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderWithQuery(<BookingPage slug={SLUG} />);
    await chooseCorteAt("09:00");

    await userEvent.type(screen.getByLabelText("Email"), "no-es-email");
    await userEvent.type(screen.getByLabelText("Teléfono (opcional)"), "abc");
    await userEvent.click(screen.getByRole("button", { name: "Confirmar reserva" }));

    expect(await screen.findByText("Pon tu nombre.")).toBeInTheDocument();
    expect(screen.getByText("Introduce un email válido.")).toBeInTheDocument();
    expect(screen.getByText("Introduce un teléfono válido (solo cifras).")).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it("says when there are no free slots this week", async () => {
    businessWithAvailability([]);
    renderWithQuery(<BookingPage slug={SLUG} />);
    await userEvent.click(await screen.findByRole("radio", { name: /Corte/ }));

    expect(await screen.findByText("No hay huecos libres esta semana. Prueba con la siguiente.")).toBeInTheDocument();
  });

  it("explains an unknown business and a business without services", async () => {
    server.use(http.get(BASE, () => HttpResponse.json({ status: 404, detail: "No existe ese negocio." }, { status: 404 })));
    const { unmount } = renderWithQuery(<BookingPage slug={SLUG} />);
    expect(await screen.findByText("No encontramos este negocio. Revisa la dirección.")).toBeInTheDocument();
    unmount();

    server.use(http.get(BASE, () => HttpResponse.json({ ...business, services: [], staff: [] })));
    renderWithQuery(<BookingPage slug={SLUG} />);
    expect(await screen.findByText("Este negocio todavía no admite reservas online.")).toBeInTheDocument();
  });
});
