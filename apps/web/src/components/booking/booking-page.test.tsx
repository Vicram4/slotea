import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import type { Availability, Booking, PublicBusiness } from "@/lib/booking";
import { addDays, todayIn } from "@/lib/dates";
import { DEMO_CUSTOMER } from "@/lib/demo";
import { server } from "@/test/msw";
import { renderWithQuery } from "@/test/render";

import { BookingPage } from "./booking-page";

const SLUG = "barberia-demo";
const BASE = `/api/v1/public/businesses/${SLUG}`;

const business: PublicBusiness = {
  slug: SLUG,
  name: "Barbería Demo",
  timezone: "Europe/Madrid",
  brandColor: "#1e3a8a",
  demo: false,
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
    manageToken: "tok_123",
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

  it("wears the business brand color and takes it off when leaving", async () => {
    businessWithAvailability();
    const { unmount } = renderWithQuery(<BookingPage slug={SLUG} />);

    expect(await screen.findByRole("heading", { name: "Barbería Demo" })).toBeInTheDocument();
    const style = document.documentElement.style;
    expect(style.getPropertyValue("--primary")).toBe("#1e3a8a");

    unmount();
    expect(style.getPropertyValue("--primary")).toBe("");
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

    // Solo Ana hace el corte: no hay a quién elegir, el resumen ya dice con quién será.
    expect(screen.queryByRole("radiogroup", { name: "¿Con quién?" })).not.toBeInTheDocument();
    expect(screen.getByLabelText("Tu reserva")).toHaveTextContent(/Corte.*30 min.*15,00 €.*Ana/);

    // Primera semana: desde hoy (en Madrid). El primer día con huecos se elige solo.
    expect(await screen.findByRole("group", { name: "Tarde" })).toBeInTheDocument();
    expect(availabilityQueries[0]!.get("from")).toBe(today);
    expect(availabilityQueries[0]!.get("to")).toBe(addDays(today, 6));
    expect(availabilityQueries[0]!.has("staffId")).toBe(false);
    expect(screen.getByRole("button", { name: /Semana anterior/ })).toBeDisabled();

    await userEvent.click(within(screen.getByRole("group", { name: "Tarde" })).getByRole("button", { name: "16:00" }));
    expect(screen.getByLabelText("Tu reserva")).toHaveTextContent(/16:00/);
    await fillCustomer();
    await userEvent.type(screen.getByLabelText("Teléfono (opcional)"), "600 000 000");
    await userEvent.click(screen.getByRole("button", { name: /^Confirmar: .*, 16:00$/ }));

    expect(await screen.findByRole("heading", { name: "Hecho. Te esperamos." })).toBeInTheDocument();
    expect(screen.getByText("laura@example.test")).toBeInTheDocument(); // «te hemos enviado un email a…»
    expect(screen.getByRole("link", { name: "Ver o cambiar mi cita" })).toHaveAttribute(
      "href",
      `/${SLUG}/cita/tok_123`,
    );
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
    // La barba la hacen dos: se elige entre ellos o «me da igual».
    const staff = screen.getByRole("radiogroup", { name: "¿Con quién?" });
    expect(within(staff).getAllByRole("radio").map((r) => r.closest("label")!.textContent)).toEqual([
      "Me da igual",
      "Ana",
      "Luis",
    ]);
    await userEvent.click(within(staff).getByRole("radio", { name: "Luis" }));
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

    await userEvent.click(screen.getByRole("button", { name: /^Confirmar:/ }));

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
    await userEvent.click(screen.getByRole("button", { name: /^Confirmar:/ }));

    expect(await screen.findByText("Pon tu nombre.")).toBeInTheDocument();
    expect(screen.getByText("Introduce un email válido.")).toBeInTheDocument();
    expect(screen.getByText("Introduce un teléfono válido (solo cifras).")).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it("says when there are no free slots this week", async () => {
    businessWithAvailability([]);
    renderWithQuery(<BookingPage slug={SLUG} />);
    await userEvent.click(await screen.findByRole("radio", { name: /Corte/ }));

    expect(await screen.findByText("Esta semana no queda nada libre. Prueba con la siguiente.")).toBeInTheDocument();
  });

  it("explains an unknown business and a business without services", async () => {
    server.use(http.get(BASE, () => HttpResponse.json({ status: 404, detail: "No existe ese negocio." }, { status: 404 })));
    const { unmount } = renderWithQuery(<BookingPage slug={SLUG} />);
    expect(await screen.findByText("No encontramos este negocio. ¿Seguro que la dirección está bien escrita?")).toBeInTheDocument();
    unmount();

    server.use(http.get(BASE, () => HttpResponse.json({ ...business, services: [], staff: [] })));
    renderWithQuery(<BookingPage slug={SLUG} />);
    expect(await screen.findByText("Este negocio todavía no tiene la agenda abierta online.")).toBeInTheDocument();
  });

  it("in the demo, explains where the emails go and fills in test data", async () => {
    businessWithAvailability();
    server.use(http.get(BASE, () => HttpResponse.json({ ...business, demo: true })));
    let body: { customer?: { fullName: string; email: string } } = {};
    server.use(
      http.post(`${BASE}/appointments`, async ({ request }) => {
        body = (await request.json()) as typeof body;
        return HttpResponse.json(
          { ...confirmation(`${addDays(today, 1)}T09:00:00+01:00`), customerEmail: DEMO_CUSTOMER.email },
          { status: 201 },
        );
      }),
    );
    renderWithQuery(<BookingPage slug={SLUG} />);

    const notice = await screen.findByRole("note");
    expect(notice).toHaveTextContent("No sale ningún email");
    expect(within(notice).getByRole("link", { name: "Bandeja de la demo" })).toHaveAttribute("href", "/panel/bandeja");

    await chooseCorteAt("09:00");
    await userEvent.click(screen.getByRole("button", { name: "Rellenar con datos de prueba" }));
    expect(screen.getByLabelText("Nombre")).toHaveValue(DEMO_CUSTOMER.fullName);
    await userEvent.click(screen.getByRole("button", { name: /^Confirmar:/ }));

    expect(await screen.findByText(/En la demo no sale ningún email/)).toBeInTheDocument();
    expect(body.customer).toMatchObject(DEMO_CUSTOMER);
    expect(screen.getByRole("link", { name: "Ver el email en la bandeja" })).toHaveAttribute("href", "/panel/bandeja");
  });

  it("outside the demo, shows neither the notice nor the test data button", async () => {
    businessWithAvailability();
    renderWithQuery(<BookingPage slug={SLUG} />);
    await chooseCorteAt("09:00");
    expect(screen.queryByRole("note")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Rellenar con datos de prueba" })).not.toBeInTheDocument();
  });
});
