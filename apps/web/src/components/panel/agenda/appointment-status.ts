import type { AgendaAppointment, AppointmentStatus } from "@/lib/agenda";

/** Color de cada estado, igual en la rejilla, en la lista y en el detalle. */
export const statusStyles: Record<AppointmentStatus, string> = {
  CONFIRMED: "border-sky-300 bg-sky-50 text-sky-950",
  COMPLETED: "border-emerald-300 bg-emerald-50 text-emerald-950",
  NO_SHOW: "border-amber-300 bg-amber-50 text-amber-950",
  CANCELLED: "border-zinc-200 bg-zinc-50 text-zinc-500 line-through",
};

/** Ya ha empezado y nadie ha dicho cómo fue: se marca con borde discontinuo para que no se olvide. */
export function isPendingOutcome(appointment: AgendaAppointment): boolean {
  return appointment.status === "CONFIRMED" && appointment.canRecordOutcome;
}
