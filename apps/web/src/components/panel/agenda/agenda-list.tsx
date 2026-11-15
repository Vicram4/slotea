"use client";

import { statusLabels, type AgendaAppointment } from "@/lib/agenda";
import { formatLongDate, type IsoDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

import { isPendingOutcome, pendingOutcomeStyle, statusStyles } from "./appointment-status";

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
    <section aria-label="Lista de citas" className="grid gap-6">
      {days.map((day) => {
        const ofDay = appointments.filter((a) => a.date === day);
        return (
          <div key={day} className="grid gap-2">
            {days.length > 1 && (
              <h3 className="text-muted-foreground text-xs font-medium uppercase">{formatLongDate(day)}</h3>
            )}
            {ofDay.length === 0 ? (
              // Con un solo día, el resumen de arriba ya dice que no hay citas.
              days.length > 1 && <p className="text-muted-foreground text-sm">Nada este día.</p>
            ) : (
              <ul className="grid gap-2">
                {ofDay.map((a) => (
                  <li key={a.id}>
                    <button
                      type="button"
                      onClick={() => onSelect(a)}
                      className={cn(
                        "grid w-full grid-cols-[3.25rem_1fr] gap-x-3 rounded-md border-l-[3px] px-3 py-2.5 text-left text-sm",
                        statusStyles[a.status],
                        isPendingOutcome(a) && pendingOutcomeStyle,
                      )}
                    >
                      <span className="font-medium tabular-nums">{a.startTime}</span>
                      <span className="font-medium">{a.customer.fullName}</span>
                      <span className="text-xs tabular-nums opacity-60">{a.endTime}</span>
                      <span className="text-xs opacity-70">
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
