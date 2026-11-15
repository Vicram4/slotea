import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import type { Me } from "@/lib/api/client";
import type { InboxEmail, InboxEmailDetail } from "@/lib/demo";
import { server } from "@/test/msw";
import { ownerMe, renderWithQuery } from "@/test/render";

import { DemoInbox, emailDocument } from "./demo-inbox";

const business = { ...ownerMe.activeBusiness!, slug: "demo-abc12345", demo: true };
const demoOwner: Me = { ...ownerMe, activeBusiness: business, memberships: [business] };

const toCustomer: InboxEmail = {
  id: "e-1",
  type: "BOOKING_CONFIRMED",
  toBusiness: false,
  to: "cliente.demo@example.com",
  subject: "Cita confirmada: Corte de pelo, lunes 23 de noviembre a las 10:00",
  createdAt: "2026-11-15T12:00:00Z",
  appointmentId: "a-1",
};
const toBusiness: InboxEmail = {
  ...toCustomer,
  id: "e-2",
  type: "BUSINESS_NEW_BOOKING",
  toBusiness: true,
  to: "barberia-demo@example.com",
  subject: "Nueva reserva: Cliente Demo, lunes 23 de noviembre a las 10:00",
};

function detail(email: InboxEmail): InboxEmailDetail {
  return { ...email, html: `<html><head></head><body><p>${email.subject}</p></body></html>`, text: email.subject };
}

function inboxApi(emails: InboxEmail[], me: Me = demoOwner) {
  server.use(
    http.get("/api/v1/me", () => HttpResponse.json(me)),
    http.get("/api/v1/demo/inbox", () => HttpResponse.json(emails)),
    http.get("/api/v1/demo/inbox/:id", ({ params }) =>
      HttpResponse.json(detail(emails.find((e) => e.id === params.id)!)),
    ),
  );
}

describe("DemoInbox", () => {
  it("lists the emails and shows the selected one in a sandboxed frame", async () => {
    inboxApi([toBusiness, toCustomer]);
    renderWithQuery(<DemoInbox />);

    const list = await screen.findByRole("list", { name: "Emails" });
    expect(within(list).getAllByRole("button")).toHaveLength(2);
    expect(within(list).getByText("Al negocio")).toBeInTheDocument();
    expect(within(list).getByText("Al cliente")).toBeInTheDocument();

    // El primero (el más reciente) se abre solo.
    const frame = await screen.findByTitle(`Contenido del email: ${toBusiness.subject}`);
    expect(frame).toHaveAttribute("sandbox", "allow-popups allow-popups-to-escape-sandbox");

    await userEvent.click(within(list).getByRole("button", { name: /Cita confirmada/ }));
    expect(await screen.findByTitle(`Contenido del email: ${toCustomer.subject}`)).toBeInTheDocument();
    expect(screen.getByText(`Para: ${toCustomer.to}`, { selector: "article p" })).toBeInTheDocument();
  });

  it("explains how to fill an empty inbox", async () => {
    inboxApi([]);
    renderWithQuery(<DemoInbox />);
    expect(await screen.findByText("Todavía no hay emails.")).toBeInTheDocument();
  });

  it("is only for the owner of a demo copy", async () => {
    inboxApi([toCustomer], { ...demoOwner, activeBusiness: { ...business, role: "STAFF" } });
    renderWithQuery(<DemoInbox />);
    expect(await screen.findByText(/solo existe en la demo, para el dueño/)).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "Emails" })).not.toBeInTheDocument();
  });
});

describe("emailDocument", () => {
  it("opens the email's links in a new tab", () => {
    expect(emailDocument('<html><head lang="es"><title>x</title></head></html>')).toBe(
      '<html><head lang="es"><base target="_blank"><title>x</title></head></html>',
    );
    expect(emailDocument("<p>sin head</p>")).toBe('<base target="_blank"><p>sin head</p>');
  });
});
