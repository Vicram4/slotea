"use client";

import { endMinutes, statusLabels, toMinutes, type AgendaAppointment } from "@/lib/agenda";
import { cn } from "@/lib/utils";

import { isPendingOutcome, pendingOutcomeStyle, statusStyles } from "./appointment-status";

/** Alto de un minuto en la rejilla: una cita de 30 min mide 42 px (dos líneas de texto). */
const PX_PER_MINUTE = 1.4;
/** Horas que se ven siempre; se amplían si hay citas antes o después. */
const DEFAULT_START = 8 * 60;
const DEFAULT_END = 20 * 60;

export type GridColumn = {
  key: string;
  title: string;
  subtitle?: string;
  /** Vista de día: la inicial del profesional, en un círculo junto a su nombre. */
  initial?: string;
  /** Hoy: la cabecera se resalta. */
  today?: boolean;
  /** Minutos de «ahora», para la línea de la hora actual (solo en la columna de hoy). */
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
    <section aria-label={label} className="bg-background overflow-x-auto rounded-xl border shadow-xs">
      <div className="grid min-w-fit" style={template}>
        <div className="bg-background sticky left-0 z-20 border-b" />
        {columns.map((column) => (
          <div key={column.key} className="flex items-center justify-center gap-2 border-b border-l px-2 py-3 text-sm">
            {column.initial !== undefined ? (
              <>
                <span
                  aria-hidden
                  className="bg-muted text-muted-foreground grid size-6 shrink-0 place-items-center rounded-full text-xs font-medium"
                >
                  {column.initial}
                </span>
                <span className="truncate font-medium">{column.title}</span>
                {column.subtitle && <span className="text-muted-foreground text-xs">· {column.subtitle}</span>}
              </>
            ) : (
              <span className="grid justify-items-center gap-0.5">
                <span className="text-muted-foreground text-xs uppercase">{column.title}</span>
                {column.subtitle && (
                  <span
                    className={cn(
                      "rounded-full px-2 py-0.5 text-sm font-medium tabular-nums",
                      column.today && "bg-primary text-primary-foreground",
                    )}
                  >
                    {column.subtitle}
                  </span>
                )}
              </span>
            )}
          </div>
        ))}

        <div className="bg-background sticky left-0 z-20" style={{ height }} aria-hidden>
          {hours.slice(0, -1).map((minute) => (
            <span
              key={minute}
              className="text-muted-foreground absolute right-2 text-[0.7rem] tabular-nums"
              style={{ top: (minute - start) * PX_PER_MINUTE + 2 }}
            >
              {hhmm(minute)}
            </span>
          ))}
        </div>
        {columns.map((column) => (
          <div key={column.key} className={cn("relative border-l", column.today && "bg-primary/[0.02]")} style={{ height }}>
            {hours.slice(0, -1).map((minute) => (
              <div key={minute} aria-hidden>
                {minute > start && (
                  <div className="absolute inset-x-0 border-t" style={{ top: (minute - start) * PX_PER_MINUTE }} />
                )}
                <div
                  className="border-border/60 absolute inset-x-0 border-t border-dashed"
                  style={{ top: (minute + 30 - start) * PX_PER_MINUTE }}
                />
              </div>
            ))}
            {column.nowMinutes !== undefined && column.nowMinutes >= start && column.nowMinutes <= end && (
              <div
                aria-hidden
                data-testid="now-line"
                className="border-primary absolute inset-x-0 z-10 border-t-2"
                style={{ top: (column.nowMinutes - start) * PX_PER_MINUTE }}
              >
                <span className="bg-primary absolute -top-[5px] -left-1 size-2 rounded-full" />
              </div>
            )}
            {lanes(column.appointments).map(({ appointment, lane, of }) => {
              const from = toMinutes(appointment.startTime);
              const to = endMinutes(appointment);
              return (
                <button
                  key={appointment.id}
                  type="button"
                  onClick={() => onSelect(appointment)}
                  // Empieza por lo que se ve («09:30 Laura»): quien usa control por voz dice lo que lee.
                  aria-label={`${appointment.startTime} ${appointment.customer.fullName}, ${appointment.serviceName}, hasta las ${appointment.endTime}, ${statusLabels[appointment.status]}`}
                  className={cn(
                    "focus-visible:ring-ring/50 absolute z-[5] flex flex-col justify-start overflow-hidden rounded-md border-l-[3px] px-2 py-0.5 text-left text-xs leading-tight transition-[filter] hover:brightness-[0.97] focus-visible:ring-[3px] focus-visible:outline-none",
                    statusStyles[appointment.status],
                    isPendingOutcome(appointment) && pendingOutcomeStyle,
                  )}
                  style={{
                    top: (from - start) * PX_PER_MINUTE + 1,
                    height: Math.max((to - from) * PX_PER_MINUTE - 2, 18),
                    left: `calc(${(lane / of) * 100}% + 3px)`,
                    width: `calc(${100 / of}% - 6px)`,
                  }}
                >
                  <span className="block truncate">
                    <span className="opacity-70 tabular-nums">{appointment.startTime}</span>{" "}
                    <span className="font-medium">{appointment.customer.fullName}</span>
                  </span>
                  {/* Una cita de menos de 30 min no tiene sitio para el servicio (sigue en el aria-label). El
                      espacio separa las palabras en el texto del botón, que tiene que casar con su nombre. */}
                  {to - from >= 30 && (
                    <>
                      {" "}
                      <span className="block truncate opacity-70">{appointment.serviceName}</span>
                    </>
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </div>
    </section>
  );
}
