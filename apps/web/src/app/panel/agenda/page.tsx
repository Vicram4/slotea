import type { Metadata } from "next";
import { Suspense } from "react";

import { AgendaRoute } from "@/components/panel/agenda/agenda-route";

export const metadata: Metadata = { title: "Agenda · Slotea" };

export default function Page() {
  return (
    <>
      <header>
        <h1 className="text-2xl font-semibold">Agenda</h1>
        <p className="text-muted-foreground text-sm">
          Las citas del día o de la semana. Pulsa una cita para ver sus datos, marcar cómo fue o cancelarla.
        </p>
      </header>
      {/* useSearchParams: se resuelve en el navegador (ver AgendaRoute). */}
      <Suspense fallback={<p role="status">Cargando la agenda…</p>}>
        <AgendaRoute />
      </Suspense>
    </>
  );
}
