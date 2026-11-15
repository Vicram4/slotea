import type { AgendaAppointment, AppointmentStatus } from "@/lib/agenda";

/**
 * Aspecto de cada cita en la rejilla y en la lista. El único color es el del negocio (las que
 * están por llegar); lo que ya pasó se apaga a gris, y «no se presentó» lleva un rojo discreto.
 */
export const statusStyles: Record<AppointmentStatus, string> = {
  CONFIRMED: "border-l-primary bg-primary/10 text-foreground",
  COMPLETED: "border-l-foreground/25 bg-muted text-muted-foreground",
  NO_SHOW: "border-l-destructive bg-destructive/5 text-muted-foreground",
  CANCELLED: "border-l-border bg-background text-muted-foreground line-through outline-1 outline-dashed -outline-offset-1 outline-border",
};

/** La etiqueta del estado en el detalle de la cita. */
export const statusBadgeStyles: Record<AppointmentStatus, string> = {
  CONFIRMED: "bg-primary/10 text-primary",
  COMPLETED: "bg-muted text-foreground",
  NO_SHOW: "bg-destructive/10 text-destructive",
  CANCELLED: "bg-muted text-muted-foreground line-through",
};

/** Ya ha empezado y nadie ha dicho cómo fue: se marca con un contorno discontinuo para que no se olvide. */
export function isPendingOutcome(appointment: AgendaAppointment): boolean {
  return appointment.status === "CONFIRMED" && appointment.canRecordOutcome;
}

export const pendingOutcomeStyle = "outline-1 outline-dashed -outline-offset-1 outline-primary";
