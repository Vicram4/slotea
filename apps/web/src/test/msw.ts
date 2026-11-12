import { setupServer } from "msw/node";

/** API simulada para los tests. Cada test añade sus respuestas con `server.use(...)`. */
export const server = setupServer();
