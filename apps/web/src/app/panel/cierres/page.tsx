import type { Metadata } from "next";

import { ClosuresPanel } from "@/components/panel/schedule/closures-panel";

export const metadata: Metadata = { title: "Cierres · Slotea" };

export default function Page() {
  return (
    <>
      <header>
        <h1 className="text-2xl font-semibold">Cierres</h1>
        <p className="text-muted-foreground text-sm">
          Días en que no se puede reservar: festivos, vacaciones del negocio y ausencias del equipo.
        </p>
      </header>
      <ClosuresPanel />
    </>
  );
}
