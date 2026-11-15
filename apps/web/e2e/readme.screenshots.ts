import { expect, test, type Page } from "@playwright/test";

import { chooseFirstSlot, fillCustomer, nextWeekday, startDemo } from "./helpers";

/**
 * Las capturas del README (`docs/img`). No es un test: `pnpm screenshots`, mejor contra la build de
 * producción (sin el indicador de desarrollo de Next): `E2E_BASE_URL=http://localhost:3001`.
 */
const IMG = "../../docs/img";

async function shot(page: Page, name: string, fullPage = false) {
  // Sin el ratón encima de nada, sin transiciones a medias ni el cursor de texto parpadeando.
  await page.mouse.move(0, 0);
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== "running"));
  await page.screenshot({ path: `${IMG}/${name}.png`, fullPage, caret: "hide" });
}

test.use({ viewport: { width: 1280, height: 800 } });

test("booking page", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 1100 });
  await startDemo(page, "Peluquería", "Reservar como cliente");
  await chooseFirstSlot(page, "Corte de mujer");
  await fillCustomer(page, "Clienta de Prueba", "clienta@example.test");
  await page.evaluate(() => window.scrollTo(0, 0));
  await shot(page, "reserva");
});

test("panel", async ({ page }) => {
  test.setTimeout(90_000);
  await startDemo(page, "Barbería", "Entrar como negocio");

  await page.setViewportSize({ width: 1440, height: 900 });
  const day = nextWeekday();
  await page.goto(`/panel/agenda?date=${day}`);
  const agenda = page.getByRole("region", { name: "Agenda del día" });
  await expect(agenda.getByRole("button").first()).toBeVisible();
  await shot(page, "agenda");

  // Cancelar una cita: el email al cliente aparece en la bandeja de la demo.
  await agenda.getByRole("button", { name: /, Confirmada$/ }).nth(2).click();
  const dialog = page.getByRole("dialog");
  await expect(dialog).toHaveCSS("opacity", "1");
  await shot(page, "cita");
  await dialog.getByRole("button", { name: "Cancelar cita" }).click();
  await dialog.getByRole("button", { name: "Sí, cancelar la cita" }).click();
  await expect(dialog).toContainText("Cancelada");
  await page.goto("/panel/bandeja");
  await expect(page.getByRole("list", { name: "Emails" })).toBeVisible();
  await shot(page, "bandeja");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`/panel/agenda?date=${day}`);
  await expect(page.getByRole("button", { name: "Menú" })).toBeVisible();
  await shot(page, "agenda-movil");
});
