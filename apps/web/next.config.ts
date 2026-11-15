import type { NextConfig } from "next";

import { securityHeaders, TOKEN_PAGE_HEADERS } from "./src/lib/security-headers";

// El navegador solo habla con este servidor: /api/* se reenvía a la API de Spring.
// Así las cookies son first-party y no hace falta CORS. Se evalúa al construir.
const apiUrl = process.env.API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
  async headers() {
    const dev = process.env.NODE_ENV === "development";
    return [
      { source: "/:path*", headers: securityHeaders({ dev }) },
      // Va después: para la página del token, esta `Referrer-Policy` sustituye a la general.
      { source: "/:slug/cita/:token", headers: TOKEN_PAGE_HEADERS },
    ];
  },
};

export default nextConfig;
