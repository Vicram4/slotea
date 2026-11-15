import type { Metadata } from "next";

import { PageHeader } from "@/components/panel/page-header";
import { BusinessSettings } from "@/components/panel/settings/business-settings";

export const metadata: Metadata = { title: "Ajustes · Slotea" };

export default function Page() {
  return (
    <>
      <PageHeader title="Ajustes" description="Los datos de tu negocio y cómo se ve tu página de reservas." />
      <BusinessSettings />
    </>
  );
}
