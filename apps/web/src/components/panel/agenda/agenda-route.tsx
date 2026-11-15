"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import { isIsoDate } from "@/lib/dates";

import { AgendaView, type AgendaLocation } from "./agenda-view";

/**
 * Traduce la URL (`?date=2026-11-23&view=week&staff=…`) a lo que enseña la agenda y al revés. Así el
 * enlace de los avisos por email abre el día correcto y el botón «atrás» funciona. Va dentro de un
 * Suspense: `useSearchParams` no se conoce al prerenderizar.
 */
export function AgendaRoute() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const date = params.get("date");
  const location: AgendaLocation = {
    date: isIsoDate(date) ? date : undefined,
    view: params.get("view") === "week" ? "week" : "day",
    staffId: params.get("staff") || undefined,
  };

  const onNavigate = (next: AgendaLocation) => {
    const query = new URLSearchParams();
    if (next.date) query.set("date", next.date);
    if (next.view === "week") query.set("view", "week");
    if (next.staffId) query.set("staff", next.staffId);
    router.push(query.size > 0 ? `${pathname}?${query}` : pathname, { scroll: false });
  };

  return <AgendaView location={location} onNavigate={onNavigate} />;
}
