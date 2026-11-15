"use client";

import { statusLabels, type AgendaAppointment } from "@/lib/agenda";
import { formatLongDate, type IsoDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

import { isPendingOutcome, statusStyles } from "./appointment-status";

/**
 * La agenda como lista, agrupada por días: en el móvil, donde no cabe la rejilla. Mismos datos y
 * mismo detalle al pulsar.
 */
export function AgendaList({
  days,
  appointments,
  showStaff,
  onSelect,
}: {
  days: IsoDate[];
  appointments: AgendaAppointment[];
  showStaff: boolean;
  onSelect: (appointment: AgendaAppointment) => void;
}) {
  return (
    <section aria-label="Lista de citas" className="grid gap-4">
      {days.map((day) => {
        const ofDay = appointments.filter((a) => a.date === day);
        return (
          <div key={day} className="grid gap-2">
            {days.length > 1 && <h3 className="text-sm font-medium first-letter:uppercase">{formatLongDate(day)}</h3>}
            {ofDay.length === 0 ? (
              <p className="text-muted-foreground text-sm">Sin citas.</p>
            ) : (
              <ul className="grid gap-2">
                {ofDay.map((a) => (
                  <li key={a.id}>
                    <button
                      type="button"
                      onClick={() => onSelect(a)}
                      className={cn(
                        "grid w-full grid-cols-[3.5rem_1fr] gap-x-3 rounded-md border p-3 text-left text-sm",
                        statusStyles[a.status],
                        isPendingOutcome(a) && "border-2 border-dashed",
                      )}
                    >
                      <span className="font-semibold tabular-nums">{a.startTime}</span>
                      <span className="font-medium">{a.customer.fullName}</span>
                      <span className="text-xs tabular-nums opacity-70">{a.endTime}</span>
                      <span className="text-xs opacity-80">
                        {a.serviceName}
                        {showStaff && ` · ${a.staffName}`} · {statusLabels[a.status]}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        );
      })}
    </section>
  );
}
