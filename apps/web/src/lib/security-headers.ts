/**
 * Cabeceras de seguridad de la web (las de la API las pone Spring Security y llegan por el rewrite).
 *
 * CSP estática, sin nonce: un nonce obliga a renderizar cada página en cada petición y se perdería el
 * prerender de `cacheComponents`. Por eso `script-src` admite `'unsafe-inline'` (Next mete inline su
 * arranque). Lo que sí cierra: scripts, conexiones, fuentes e imágenes de otros orígenes, que nos
 * metan en un iframe, `<base>` y formularios hacia fuera, y plugins. Ver docs/adr.
 */

type Header = { key: string; value: string };

export function contentSecurityPolicy({ dev }: { dev: boolean }): string {
  const directives: Record<string, string[]> = {
    "default-src": ["'self'"],
    // Turbopack en desarrollo evalúa código (HMR).
    "script-src": ["'self'", "'unsafe-inline'", ...(dev ? ["'unsafe-eval'"] : [])],
    // Atributos `style` de React (el color de marca va en variables CSS) y el HTML de los emails de la demo.
    "style-src": ["'self'", "'unsafe-inline'"],
    "img-src": ["'self'", "data:", "blob:"],
    // next/font sirve Inter desde el propio dominio.
    "font-src": ["'self'"],
    // Todo pasa por /api en el mismo origen; en desarrollo, el websocket del HMR.
    "connect-src": ["'self'", ...(dev ? ["ws:"] : [])],
    // El iframe de la bandeja usa `srcdoc` (hereda esta política) y no carga nada de fuera.
    "frame-src": ["'self'"],
    "frame-ancestors": ["'none'"],
    "object-src": ["'none'"],
    "base-uri": ["'self'"],
    "form-action": ["'self'"],
  };
  const policy = Object.entries(directives).map(([name, sources]) => `${name} ${sources.join(" ")}`);
  if (!dev) policy.push("upgrade-insecure-requests");
  return policy.join("; ");
}

export function securityHeaders({ dev }: { dev: boolean }): Header[] {
  return [
    { key: "Content-Security-Policy", value: contentSecurityPolicy({ dev }) },
    { key: "X-Content-Type-Options", value: "nosniff" },
    // Para navegadores sin `frame-ancestors`.
    { key: "X-Frame-Options", value: "DENY" },
    { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
    { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=(), usb=()" },
    // Dos años; solo tiene efecto en HTTPS (Vercel).
    { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
  ];
}

/**
 * La página para gestionar una cita lleva el token en la URL: ni siquiera el origen sale en el
 * `Referer` (además de la etiqueta `<meta name="referrer">` de la página).
 */
export const TOKEN_PAGE_HEADERS: Header[] = [{ key: "Referrer-Policy", value: "no-referrer" }];
