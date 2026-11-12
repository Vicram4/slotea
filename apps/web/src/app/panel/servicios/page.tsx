import type { Metadata } from "next";

import { ServicesPage } from "@/components/panel/services/services-page";

export const metadata: Metadata = { title: "Servicios · Slotea" };

export default function Page() {
  return <ServicesPage />;
}
