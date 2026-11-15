import type { Metadata } from "next";

import { DemoInbox } from "@/components/demo/demo-inbox";

export const metadata: Metadata = { title: "Bandeja de la demo · Slotea" };

export default function Page() {
  return <DemoInbox />;
}
