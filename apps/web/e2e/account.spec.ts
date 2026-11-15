import { expect, test } from "@playwright/test";

import { field } from "./helpers";

test("a business owner signs up, logs out and logs back in", async ({ page }) => {
  const id = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `e2e-${id}@example.test`;
  const password = "una-contraseña-larga";

  await page.goto("/registro");
  await field(page, "Nombre del negocio").fill(`Negocio E2E ${id}`);
  await field(page, "Tu nombre").fill("Persona de Prueba");
  await field(page, "Email").fill(email);
  await field(page, "Contraseña").fill(password);
  await page.getByRole("button", { name: "Crear cuenta" }).click();

  // Un negocio nuevo: el inicio le dice qué falta para recibir reservas.
  await expect(page.getByRole("heading", { name: "Hola, Persona de Prueba" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Configura tu negocio" })).toBeVisible();

  await page.getByRole("button", { name: "Cerrar sesión" }).click();
  await expect(page).toHaveURL(/\/login$/);

  await field(page, "Email").fill(email);
  await field(page, "Contraseña").fill(password);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page.getByRole("heading", { name: "Hola, Persona de Prueba" })).toBeVisible();
});

test("the panel sends visitors without a session to the login page", async ({ page }) => {
  await page.goto("/panel/agenda");
  await expect(page).toHaveURL(/\/login$/);
});
