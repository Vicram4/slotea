import { defineConfig, devices } from "@playwright/test";

/**
 * Tests de punta a punta contra la app de verdad: web + API + PostgreSQL. En local, contra `pnpm dev`
 * (y la API y la BD de docker compose) ya arrancados; en la CI, el job `e2e` levanta la API y aquí se
 * arranca la build de producción de la web.
 */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const ci = !!process.env.CI;

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  reporter: ci ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL,
    locale: "es-ES",
    // La de los negocios de la demo: así no sale el aviso de «horas en otra zona horaria».
    timezoneId: "Europe/Madrid",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    // Las capturas del README: solo con `pnpm screenshots` (no corren con los tests).
    ...(process.env.npm_lifecycle_event === "screenshots"
      ? [
          {
            name: "screenshots",
            testMatch: /\.screenshots\.ts$/,
            // El campo de fecha usa el idioma del navegador, no el de la página.
            use: { ...devices["Desktop Chrome"], launchOptions: { args: ["--lang=es-ES"] } },
          },
        ]
      : []),
  ],
  webServer: ci
    ? {
        // Puerto explícito: en la CI, `PORT` es el de la API y `next start` lo cogería.
        command: `pnpm start --port ${new URL(baseURL).port || 3000}`,
        url: baseURL,
        reuseExistingServer: false,
        timeout: 60_000,
      }
    : undefined,
});
