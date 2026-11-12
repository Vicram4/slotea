import type { Me } from "@/lib/api/client";
import type { Service, StaffMember } from "@/lib/catalog";

import { ownerMe } from "./render";

/** Datos ficticios para los tests del catálogo. */

export const staffMe: Me = {
  ...ownerMe,
  user: { id: "0193a1b2-0000-7000-8000-000000000002", email: "luis@example.test", fullName: "Luis Demo" },
  activeBusiness: { ...ownerMe.activeBusiness!, role: "STAFF" },
};

export const corte: Service = {
  id: "0193a1b2-0000-7000-8000-00000000c001",
  name: "Corte",
  description: "Lavado incluido",
  durationMin: 30,
  bufferMin: 5,
  priceCents: 1500,
  active: true,
  createdAt: "2026-11-01T10:00:00Z",
};

export const tinte: Service = {
  id: "0193a1b2-0000-7000-8000-00000000c002",
  name: "Tinte",
  description: null,
  durationMin: 90,
  bufferMin: 0,
  priceCents: null,
  active: false,
  createdAt: "2026-11-01T10:00:00Z",
};

export const ana: StaffMember = {
  id: "0193a1b2-0000-7000-8000-00000000a001",
  displayName: "Ana",
  userId: null,
  active: true,
  serviceIds: [corte.id],
  createdAt: "2026-11-01T10:00:00Z",
};
