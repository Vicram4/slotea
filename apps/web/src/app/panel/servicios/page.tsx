import type { Metadata } from "next";

import { ServicesPage } from "@/components/panel/services/services-page";

export const metadata: Metadata = { title: "Servicios · Slotea" };
// El panel se carga en el navegador (sesión en cookies httpOnly): no hay navegación instantánea que validar.
export const instant = false;

export default function Page() {
  return <ServicesPage />;
}
