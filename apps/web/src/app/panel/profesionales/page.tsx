import type { Metadata } from "next";

import { StaffListPage } from "@/components/panel/staff/staff-list-page";

export const metadata: Metadata = { title: "Profesionales · Slotea" };

export default function Page() {
  return <StaffListPage />;
}
