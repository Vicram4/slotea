import { expect, type Page } from "@playwright/test";

/**
 * Abre una copia de la demo desde la portada, como un visitante: elige el sector y cómo entrar. Cada
 * llamada crea una copia nueva (los tests no se pisan entre sí).
 */
export async function startDemo(page: Page, sector: string, entry: string) {
  await page.goto("/");
  await page.getByRole("button", { name: new RegExp(`^${sector}`) }).click();
  await page.getByRole("button", { name: new RegExp(`^${entry}`) }).click();
  await page.waitForURL((url) => url.pathname !== "/", { timeout: 30_000 });
}

/**
 * Un campo visible por su etiqueta. Con `cacheComponents`, Next guarda oculta (en un `<Activity>`) la
 * página de la que se viene, así que puede haber otro «Email» en el DOM.
 */
export const field = (page: Page, label: string) => page.getByLabel(label, { exact: true }).filter({ visible: true });

/**
 * En la página pública de reservas: servicio, profesional (si se elige) y el primer hueco libre. Los
 * radios van ocultos dentro de su etiqueta (se pinta la etiqueta): el clic llega a la etiqueta.
 */
export async function chooseFirstSlot(page: Page, service: string, staff?: string): Promise<string> {
  await page.getByRole("radio", { name: new RegExp(service) }).check({ force: true });
  if (staff) {
    await page
      .getByRole("radiogroup", { name: "¿Con quién?" })
      .getByRole("radio", { name: staff })
      .check({ force: true });
  }
  const slot = page.getByRole("group", { name: /^(Mañana|Tarde|Noche)$/ }).getByRole("button").first();
  await expect(slot).toBeVisible();
  const time = (await slot.textContent())!.trim();
  await slot.click();
  return time;
}

export async function fillCustomer(page: Page, name: string, email: string) {
  await field(page, "Nombre").fill(name);
  await field(page, "Email").fill(email);
}

/** El próximo día de lunes a viernes (en Madrid), en ISO: la demo tiene la agenda llena esos días. */
export function nextWeekday(): string {
  const madrid = (date: Date) =>
    new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Madrid", weekday: "short" }).formatToParts(date);
  for (let days = 1; days <= 7; days++) {
    const date = new Date(Date.now() + days * 86_400_000);
    const weekday = madrid(date).find((p) => p.type === "weekday")!.value;
    if (!["Sat", "Sun"].includes(weekday)) {
      return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Madrid" }).format(date);
    }
  }
  throw new Error("unreachable");
}
