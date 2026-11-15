import type { Metadata } from "next";

import { BusinessSettings } from "@/components/panel/settings/business-settings";

export const metadata: Metadata = { title: "Ajustes · Slotea" };

export default function Page() {
  return (
    <>
      <header>
        <h1 className="text-2xl font-semibold">Ajustes</h1>
        <p className="text-muted-foreground text-sm">Los datos de tu negocio y cómo se ve tu página de reservas.</p>
      </header>
      <BusinessSettings />
    </>
  );
}
