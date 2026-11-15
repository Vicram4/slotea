import type { Metadata } from "next";

import { PageHeader } from "@/components/panel/page-header";
import { ClosuresPanel } from "@/components/panel/schedule/closures-panel";

export const metadata: Metadata = { title: "Cierres · Slotea" };

export default function Page() {
  return (
    <>
      <PageHeader
        title="Cierres"
        description="Festivos, vacaciones del negocio y ausencias del equipo: los días en que no se puede reservar."
      />
      <ClosuresPanel />
    </>
  );
}
