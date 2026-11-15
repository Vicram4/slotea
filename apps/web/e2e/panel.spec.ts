import { expect, test } from "@playwright/test";

import { nextWeekday, startDemo } from "./helpers";

test("the owner cancels an appointment and the customer's email shows up in the demo inbox", async ({ page }) => {
  await startDemo(page, "Barbería", "Entrar como negocio");
  await expect(page).toHaveURL(/\/panel/);

  await page.goto(`/panel/agenda?date=${nextWeekday()}`);
  const agenda = page.getByRole("region", { name: "Agenda del día" });
  await agenda.getByRole("button", { name: /, Confirmada$/ }).first().click();

  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "Cancelar cita" }).click();
  await dialog.getByRole("button", { name: "Sí, cancelar la cita" }).click();
  await expect(dialog).toContainText("Cancelada");
  await page.keyboard.press("Escape");

  await page.getByRole("navigation", { name: "Panel" }).getByRole("link", { name: "Bandeja de la demo" }).click();
  const inbox = page.getByRole("list", { name: "Emails" });
  await expect(inbox.getByRole("button", { name: /Cita cancelada/ })).toBeVisible();
});

test("an employee only sees their part of the panel", async ({ page }) => {
  await startDemo(page, "Barbería", "Entrar como empleado");

  const nav = page.getByRole("navigation", { name: "Panel" });
  await expect(nav.getByRole("link", { name: "Agenda" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Ajustes" })).toHaveCount(0);
  await expect(nav.getByRole("link", { name: "Bandeja de la demo" })).toHaveCount(0);

  // Lo decide la API, no el menú: la página de ajustes no deja cambiar nada.
  await page.goto("/panel/ajustes");
  await expect(page.getByRole("button", { name: "Guardar cambios" })).toHaveCount(0);
});

test.describe("on a phone", () => {
  test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });

  test("the navigation folds into a menu", async ({ page }) => {
    await startDemo(page, "Barbería", "Entrar como negocio");

    const menu = page.getByRole("button", { name: "Menú" });
    await expect(menu).toHaveAttribute("aria-expanded", "false");
    await expect(page.getByRole("link", { name: "Servicios" })).toBeHidden();

    await menu.click();
    await page.getByRole("link", { name: "Servicios" }).click();

    await expect(page.getByRole("heading", { name: "Servicios", level: 1 })).toBeVisible();
    await expect(menu).toHaveAttribute("aria-expanded", "false");
  });
});
