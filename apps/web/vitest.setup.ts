import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";

import { clearCookies } from "./src/test/render";
import { server } from "./src/test/msw";

// jsdom no implementa ResizeObserver; Radix (Switch, Checkbox) lo usa para medir el control.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
};

// Una petición sin respuesta simulada es un error: así ningún test depende de la red real.
beforeAll(() => server.listen({ onUnhandledFrame: "error" }));

afterEach(() => {
  cleanup();
  server.resetHandlers();
  clearCookies();
});

afterAll(() => server.close());
