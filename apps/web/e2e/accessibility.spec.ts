import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

import { chooseFirstSlot, fillCustomer, nextWeekday, startDemo } from "./helpers";

/** WCAG 2.2 A y AA (lo que axe puede comprobar solo; el resto, a mano y con lector de pantalla). */
async function expectNoViolations(page: Page, name: string) {
  // A mitad de una transición (un botón que se pinta del color de marca, un diálogo que aparece) axe
  // mide un contraste que no es el real.
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== "running"));
  const { violations } = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"])
    .analyze();
  const summary = violations.map(
    (v) => `${v.id} (${v.impact}): ${v.help}\n    ${v.nodes.map((n) => n.target.join(" ")).join("\n    ")}`,
  );
  // `soft`: un solo pase enseña los problemas de todas las páginas.
  expect.soft(summary, `Problemas de accesibilidad en ${name}`).toEqual([]);
}

test("public pages", async ({ page }) => {
  for (const path of ["/", "/login", "/registro", "/privacidad"]) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    await expectNoViolations(page, path);
  }
});

test("booking as a guest", async ({ page }) => {
  await startDemo(page, "Peluquería", "Reservar como cliente");
  await expectNoViolations(page, "la página de reservas");

  await chooseFirstSlot(page, "Corte de mujer");
  await expect(page.getByLabel("Nombre", { exact: true })).toBeVisible();
  await expectNoViolations(page, "la página de reservas con hueco elegido");

  await fillCustomer(page, "Clienta de Prueba", "clienta@example.test");
  await page.getByRole("button", { name: /^Confirmar:/ }).click();
  await expect(page.getByRole("heading", { name: "Hecho. Te esperamos." })).toBeVisible();
  await expectNoViolations(page, "la confirmación de la reserva");

  await page.getByRole("link", { name: "Ver o cambiar mi cita" }).click();
  await expect(page.getByRole("button", { name: "Cancelar cita" })).toBeVisible();
  await expectNoViolations(page, "la página para gestionar la cita");
});

test("the panel", async ({ page }) => {
  test.setTimeout(90_000);
  await startDemo(page, "Barbería", "Entrar como negocio");

  const pages: [string, string][] = [
    ["/panel", "Hola"],
    [`/panel/agenda?date=${nextWeekday()}`, ""],
    [`/panel/agenda?date=${nextWeekday()}&view=week`, ""],
    ["/panel/servicios", "Servicios"],
    ["/panel/profesionales", "Profesionales"],
    ["/panel/cierres", "Cierres"],
    ["/panel/ajustes", "Ajustes"],
    ["/panel/bandeja", "Bandeja de la demo"],
  ];
  for (const [path, heading] of pages) {
    await page.goto(path);
    await expect(page.getByRole("heading", { level: 1, name: new RegExp(heading) })).toBeAttached();
    await expect(page.getByRole("status").filter({ hasText: /^Cargando/ })).toHaveCount(0);
    await expectNoViolations(page, path);
  }

  // Una ficha de profesional y el diálogo de una cita.
  await page.goto("/panel/profesionales");
  await page.getByRole("link", { name: /^Nacho/ }).click();
  await expect(page.getByRole("tab", { name: "Horario" })).toBeVisible();
  await expectNoViolations(page, "la ficha de un profesional");
  await page.getByRole("tab", { name: "Horario" }).click();
  await expect(page.getByText(/a la semana/)).toBeVisible();
  await expectNoViolations(page, "el horario de un profesional");

  await page.goto(`/panel/agenda?date=${nextWeekday()}`);
  await page.getByRole("region", { name: "Agenda del día" }).getByRole("button").first().click();
  // Al final de la animación de entrada: a media opacidad, axe mide un contraste que no es el real.
  await expect(page.getByRole("dialog")).toHaveCSS("opacity", "1");
  await expectNoViolations(page, "el diálogo de una cita");
});
