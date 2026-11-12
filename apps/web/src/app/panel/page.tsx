import type { Metadata } from "next";

import { PanelHome } from "@/components/panel/panel-home";

export const metadata: Metadata = { title: "Panel · Slotea" };

/** El panel se pinta en el cliente: los datos dependen de la sesión (cookies httpOnly). */
export default function PanelPage() {
  return (
    <main className="mx-auto grid max-w-2xl gap-6 px-6 py-12">
      <PanelHome />
    </main>
  );
}
