import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement } from "react";

import type { Me } from "@/lib/api/client";

/** Renderiza con un QueryClient nuevo (sin reintentos ni caché compartida entre tests). */
export function renderWithQuery(ui: ReactElement, options?: RenderOptions) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return {
    queryClient,
    ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>, options),
  };
}

/** Borra las cookies que el código lee (en jsdom, `document.cookie` es el único almacén). */
export function clearCookies() {
  for (const cookie of document.cookie.split("; ")) {
    const name = cookie.split("=")[0];
    if (name) document.cookie = `${name}=; max-age=0; path=/`;
  }
}

export const ownerMe: Me = {
  user: { id: "0193a1b2-0000-7000-8000-000000000001", email: "ana@example.test", fullName: "Ana Demo" },
  activeBusiness: {
    businessId: "0193a1b2-0000-7000-8000-0000000000b1",
    slug: "barberia-demo",
    name: "Barbería Demo",
    role: "OWNER",
  },
  memberships: [
    {
      businessId: "0193a1b2-0000-7000-8000-0000000000b1",
      slug: "barberia-demo",
      name: "Barbería Demo",
      role: "OWNER",
    },
  ],
};
