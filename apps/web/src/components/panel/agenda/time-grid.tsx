"use client";

import { endMinutes, statusLabels, toMinutes, type AgendaAppointment } from "@/lib/agenda";
import { cn } from "@/lib/utils";

import { isPendingOutcome, statusStyles } from "./appointment-status";

/** Alto de un minuto en la rejilla: una cita de 30 min mide 42 px (dos líneas de texto). */
const PX_PER_MINUTE = 1.4;
/** Horas que se ven siempre; se amplían si hay citas antes o después. */
const DEFAULT_START = 8 * 60;
const DEFAULT_END = 20 * 60;

export type GridColumn = {
  key: string;
  title: string;
  subtitle?: string;
  /** Hoy: la cabecera se resalta. */
  today?: boolean;
  /** Minutos de «ahora», para la línea roja (solo en la columna de hoy). */
  nowMinutes?: number;
  appointments: AgendaAppointment[];
};

/** De la primera a la última hora con citas, como mínimo de 08:00 a 20:00, en horas enteras. */
export function visibleRange(appointments: AgendaAppointment[]): { start: number; end: number } {
  let start = DEFAULT_START;
  let end = DEFAULT_END;
  for (const a of appointments) {
    start = Math.min(start, Math.floor(toMinutes(a.startTime) / 60) * 60);
    end = Math.max(end, Math.ceil(endMinutes(a) / 60) * 60);
  }
  return { start, end };
}

/**
 * Coloca cada cita en un carril. Dos citas activas del mismo profesional nunca se solapan (lo impide
 * la BD), pero una cancelada sí puede coincidir con la que ocupó su hueco: entonces van lado a lado.
 */
function lanes(appointments: AgendaAppointment[]): { appointment: AgendaAppointment; lane: number; of: number }[] {
  const sorted = [...appointments].sort((a, b) => toMinutes(a.startTime) - toMinutes(b.startTime));
  const laneEnds: number[] = [];
  const placed = sorted.map((appointment) => {
    const start = toMinutes(appointment.startTime);
    let lane = laneEnds.findIndex((end) => end <= start);
    if (lane === -1) lane = laneEnds.length;
    laneEnds[lane] = endMinutes(appointment);
    return { appointment, lane };
  });
  return placed.map((p) => ({ ...p, of: laneEnds.length }));
}

function hhmm(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}`;
}

/**
 * Rejilla horaria: una columna por profesional (vista de día) o por día (vista de semana). Cada cita
 * es un botón que abre su detalle. En pantallas pequeñas se usa `AgendaList` en su lugar.
 */
export function TimeGrid({
  columns,
  onSelect,
  label,
}: {
  columns: GridColumn[];
  onSelect: (appointment: AgendaAppointment) => void;
  label: string;
}) {
  const { start, end } = visibleRange(columns.flatMap((c) => c.appointments));
  const height = (end - start) * PX_PER_MINUTE;
  const hours = Array.from({ length: (end - start) / 60 + 1 }, (_, i) => start + i * 60);
  const template = { gridTemplateColumns: `3.5rem repeat(${columns.length}, minmax(9rem, 1fr))` };

  return (
    <section aria-label={label} className="overflow-x-auto rounded-lg border">
      <div className="grid min-w-fit" style={template}>
        <div className="border-b" />
        {columns.map((column) => (
          <div
            key={column.key}
            className={cn("border-b border-l px-2 py-2 text-center text-sm", column.today && "bg-accent")}
          >
            <div className="font-medium">{column.title}</div>
            {column.subtitle && <div className="text-muted-foreground text-xs">{column.subtitle}</div>}
          </div>
        ))}

        <div className="relative" style={{ height }} aria-hidden>
          {hours.map((minute) => (
            <span
              key={minute}
              className="text-muted-foreground absolute right-2 -translate-y-1/2 text-xs tabular-nums"
              style={{ top: (minute - start) * PX_PER_MINUTE }}
            >
              {minute < end ? hhmm(minute) : ""}
            </span>
          ))}
        </div>
        {columns.map((column) => (
          <div key={column.key} className="relative border-l" style={{ height }}>
            {hours.slice(1, -1).map((minute) => (
              <div
                key={minute}
                aria-hidden
                className="border-muted absolute inset-x-0 border-t"
                style={{ top: (minute - start) * PX_PER_MINUTE }}
              />
            ))}
            {column.nowMinutes !== undefined && column.nowMinutes >= start && column.nowMinutes <= end && (
              <div
                aria-hidden
                data-testid="now-line"
                className="absolute inset-x-0 z-10 border-t-2 border-red-500"
                style={{ top: (column.nowMinutes - start) * PX_PER_MINUTE }}
              />
            )}
            {lanes(column.appointments).map(({ appointment, lane, of }) => {
              const from = toMinutes(appointment.startTime);
              const to = endMinutes(appointment);
              return (
                <button
                  key={appointment.id}
                  type="button"
                  onClick={() => onSelect(appointment)}
                  aria-label={`${appointment.startTime}–${appointment.endTime}, ${appointment.customer.fullName}, ${appointment.serviceName}, ${statusLabels[appointment.status]}`}
                  className={cn(
                    "focus-visible:ring-ring absolute overflow-hidden rounded-md border px-2 py-1 text-left text-xs leading-tight shadow-xs hover:brightness-95 focus-visible:ring-2 focus-visible:outline-none",
                    statusStyles[appointment.status],
                    isPendingOutcome(appointment) && "border-2 border-dashed",
                  )}
                  style={{
                    top: (from - start) * PX_PER_MINUTE + 1,
                    height: Math.max((to - from) * PX_PER_MINUTE - 2, 18),
                    left: `calc(${(lane / of) * 100}% + 2px)`,
                    width: `calc(${100 / of}% - 4px)`,
                  }}
                >
                  <span className="font-semibold tabular-nums">{appointment.startTime}</span>{" "}
                  <span className="font-medium">{appointment.customer.fullName}</span>
                  <span className="block truncate opacity-80">{appointment.serviceName}</span>
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </section>
  );
}
