/**
 * `fetch` del cliente de la API con las dos piezas de la sesión:
 *
 * 1. **CSRF** (double submit cookie): en cada petición que modifica datos, copia la cookie
 *    `XSRF-TOKEN` en la cabecera `X-XSRF-TOKEN`. Si aún no hay cookie, la pide a `/auth/csrf`.
 * 2. **Refresh transparente**: si la API responde 401 (el JWT de 15 min ha caducado), renueva la
 *    sesión con el refresh token y repite la petición una vez.
 *
 * El refresh es *single-flight*: si varias peticiones reciben 401 a la vez, comparten una sola
 * renovación. Dos renovaciones con el mismo refresh token se tratarían como un robo en la API y
 * cerrarían la sesión.
 */

export const CSRF_COOKIE = "XSRF-TOKEN";
export const CSRF_HEADER = "X-XSRF-TOKEN";

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);
/** Las rutas de auth no se reintentan: un 401 ahí es la respuesta definitiva. */
const AUTH_PATH = /^\/api\/v1\/auth\//;

export function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const cookie = document.cookie.split("; ").find((c) => c.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

/** Devuelve el token CSRF, pidiéndolo a la API la primera vez. */
export async function csrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  await fetch(new URL("/api/v1/auth/csrf", location.origin), { credentials: "same-origin" });
  return readCookie(CSRF_COOKIE);
}

async function withCsrf(request: Request): Promise<Request> {
  if (!MUTATING_METHODS.has(request.method)) return request;
  const token = await csrfToken();
  if (token) request.headers.set(CSRF_HEADER, token);
  return request;
}

let refreshing: Promise<boolean> | null = null;

/** Renueva la sesión. Las llamadas simultáneas comparten la misma petición. */
export function refreshSession(): Promise<boolean> {
  refreshing ??= (async () => {
    const request = await withCsrf(
      new Request(new URL("/api/v1/auth/refresh", location.origin), {
        method: "POST",
        credentials: "same-origin",
      }),
    );
    const response = await fetch(request);
    return response.ok;
  })()
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

export async function authFetch(request: Request): Promise<Response> {
  const retry = request.clone();
  const response = await fetch(await withCsrf(request));
  if (response.status !== 401 || AUTH_PATH.test(new URL(request.url).pathname)) {
    return response;
  }
  if (!(await refreshSession())) {
    return response;
  }
  return fetch(await withCsrf(retry));
}
