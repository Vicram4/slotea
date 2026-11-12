import createClient from "openapi-fetch";

import { authFetch } from "./auth-fetch";
import type { components, paths } from "./schema";

/**
 * Cliente tipado de la API. Los tipos salen de `openapi.json` (`pnpm api:types`): si la API cambia
 * un endpoint, TypeScript avisa aquí. Solo se usa en el navegador: las rutas son relativas al
 * propio dominio y Next las reenvía a la API (cookies first-party, sin CORS).
 */
export const api = createClient<paths>({
  baseUrl: globalThis.location?.origin ?? "",
  credentials: "same-origin",
  fetch: authFetch,
});

export type Schemas = components["schemas"];
export type Me = Schemas["MeResponse"];
export type Role = Schemas["MembershipSummary"]["role"];

/** Cuerpo de error de la API (RFC 9457), con la lista de campos inválidos si es un 400. */
export type Problem = {
  status?: number;
  title?: string;
  detail?: string;
  errors?: { field: string; message: string | null }[];
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem,
  ) {
    super(problem.detail ?? `HTTP ${status}`);
    this.name = "ApiError";
  }
}

/** Convierte el resultado de openapi-fetch en datos o en un `ApiError`. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.error !== undefined || !result.response.ok) {
    throw new ApiError(result.response.status, (result.error ?? {}) as Problem);
  }
  return result.data as T;
}
