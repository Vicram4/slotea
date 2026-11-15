import type { Metadata } from "next";

import { StaffDetail } from "@/components/panel/staff/staff-detail";

export const metadata: Metadata = { title: "Profesional · Slotea" };
// El panel se carga en el navegador (sesión en cookies httpOnly): no hay navegación instantánea que validar.
export const instant = false;

/** Next 16: `params` es asíncrono. */
export default async function Page({ params }: PageProps<"/panel/profesionales/[id]">) {
  const { id } = await params;
  return <StaffDetail staffId={id} />;
}
