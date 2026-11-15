import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  resolve: { tsconfigPaths: true },
  test: {
    environment: "jsdom",
    // Los `*.spec.ts` de e2e/ son de Playwright.
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./vitest.setup.ts"],
    restoreMocks: true,
    // Zona horaria fija: el runner de CI está en UTC y sin esto los tests dependerían del equipo.
    // El caso «navegador en otra zona» se prueba aparte, simulando la zona del navegador.
    env: { TZ: "Europe/Madrid" },
  },
});