import type { Metadata } from "next";

import { PanelHome } from "@/components/panel/panel-home";

export const metadata: Metadata = { title: "Panel · Slotea" };
// El panel se carga en el navegador (sesión en cookies httpOnly): no hay navegación instantánea que validar.
export const instant = false;

export default function PanelPage() {
  return <PanelHome />;
}
