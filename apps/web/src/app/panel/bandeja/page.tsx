import type { Metadata } from "next";

import { DemoInbox } from "@/components/demo/demo-inbox";

export const metadata: Metadata = { title: "Bandeja de la demo · Slotea" };
// El panel se carga en el navegador (sesión en cookies httpOnly): no hay navegación instantánea que validar.
export const instant = false;

export default function Page() {
  return <DemoInbox />;
}
