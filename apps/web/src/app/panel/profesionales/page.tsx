import type { Metadata } from "next";

import { StaffListPage } from "@/components/panel/staff/staff-list-page";

export const metadata: Metadata = { title: "Profesionales · Slotea" };
// El panel se carga en el navegador (sesión en cookies httpOnly): no hay navegación instantánea que validar.
export const instant = false;

export default function Page() {
  return <StaffListPage />;
}
