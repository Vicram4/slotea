import type { Metadata } from "next";
import { Suspense } from "react";

import { AgendaRoute } from "@/components/panel/agenda/agenda-route";

export const metadata: Metadata = { title: "Agenda · Slotea" };

export default function Page() {
  return (
    <>
      {/* El título visible es la fecha (lo pinta la agenda). */}
      <h1 className="sr-only">Agenda</h1>
      {/* useSearchParams: se resuelve en el navegador (ver AgendaRoute). */}
      <Suspense fallback={<p role="status">Cargando la agenda…</p>}>
        <AgendaRoute />
      </Suspense>
    </>
  );
}
