import { expect, test } from "@playwright/test";

import { chooseFirstSlot, fillCustomer, startDemo } from "./helpers";

test("a guest books an appointment and cancels it with the link", async ({ page }) => {
  await startDemo(page, "Barbería", "Reservar como cliente");

  await chooseFirstSlot(page, "Corte de pelo");
  await fillCustomer(page, "Clienta de Prueba", "clienta@example.test");
  await page.getByRole("button", { name: /^Confirmar:/ }).click();

  await expect(page.getByRole("heading", { name: "Hecho. Te esperamos." })).toBeVisible();

  // El mismo enlace que llega por email (con el token), sin cuenta.
  await page.getByRole("link", { name: "Ver o cambiar mi cita" }).click();
  await expect(page.getByLabel("Tu cita")).toContainText("Clienta de Prueba");
  await page.getByRole("button", { name: "Cancelar cita" }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Sí, cancelar" }).click();

  await expect(page.getByText("Tu cita está cancelada.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Esta cita está cancelada" })).toBeVisible();
});

test("two guests cannot take the same slot", async ({ page, browser }) => {
  await startDemo(page, "Barbería", "Reservar como cliente");
  const bookingPage = page.url();

  // Otro cliente, con su propio navegador, abre la misma página a la vez.
  const other = await (await browser.newContext()).newPage();
  await other.goto(bookingPage);

  const time = await chooseFirstSlot(page, "Corte de pelo", "Nacho");
  await chooseFirstSlot(other, "Corte de pelo", "Nacho", time);
  await fillCustomer(page, "Primera Clienta", "primera@example.test");
  await fillCustomer(other, "Segunda Clienta", "segunda@example.test");

  await page.getByRole("button", { name: /^Confirmar:/ }).click();
  await expect(page.getByRole("heading", { name: "Hecho. Te esperamos." })).toBeVisible();

  // La API rechaza la segunda (409) y la página le pide otra hora. La carrera de verdad (dos
  // inserciones a la vez contra la restricción EXCLUDE) la prueban los tests de la API con Testcontainers.
  await other.getByRole("button", { name: /^Confirmar:/ }).click();
  await expect(other.getByText(/acaba de ocuparse|ya no está disponible/)).toBeVisible();
  await expect(other.getByRole("heading", { name: "Hecho. Te esperamos." })).not.toBeVisible();
});
