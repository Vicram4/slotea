"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { type Availability, HORIZON_DAYS, type Slot } from "@/lib/booking";
import { addDays, formatLongDate, isOtherTimeZone, type IsoDate, shortDay } from "@/lib/dates";
import { cn } from "@/lib/utils";

/**
 * Tira de 7 días y horas libres, agrupadas en mañana y tarde. No pide los huecos: recibe la
 * consulta ya hecha, así sirve tanto para reservar como para cambiar la hora de una cita.
 */
export function TimePicker({
  availability,
  timezone,
  today,
  weekStart,
  onWeekChange,
  date,
  onDateChange,
  slot,
  onSlotChange,
}: {
  availability: UseQueryResult<Availability>;
  timezone: string;
  today: IsoDate;
  weekStart: IsoDate;
  onWeekChange: (start: IsoDate) => void;
  date: IsoDate | null;
  onDateChange: (date: IsoDate) => void;
  slot: Slot | null;
  onSlotChange: (slot: Slot, date: IsoDate) => void;
}) {
  const lastDay = addDays(today, HORIZON_DAYS);

  if (availability.isPending) return <p role="status">Buscando huecos…</p>;
  if (availability.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudieron cargar los huecos. Inténtalo de nuevo.</AlertDescription>
      </Alert>
    );
  }

  const days = availability.data.days;
  // Si no se ha elegido día (o ya no está en esta semana), el primero con huecos.
  const selected = days.find((d) => d.date === date) ?? days.find((d) => d.slots.length > 0) ?? null;
  const morning = selected?.slots.filter((s) => s.time < "14:00") ?? [];
  const afternoon = selected?.slots.filter((s) => s.time >= "14:00") ?? [];

  return (
    <div className="grid gap-4">
      <div className="flex items-center gap-2">
        <Button
          variant="outline"
          size="icon"
          aria-label="Semana anterior"
          disabled={weekStart <= today}
          onClick={() => onWeekChange(addDays(weekStart, -7) < today ? today : addDays(weekStart, -7))}
        >
          <ChevronLeft aria-hidden />
        </Button>
        <div className="grid flex-1 grid-cols-7 gap-1" aria-busy={availability.isFetching}>
          {days.map((day) => {
            const label = shortDay(day.date);
            const empty = day.slots.length === 0;
            return (
              <button
                key={day.date}
                type="button"
                disabled={empty}
                aria-pressed={day.date === selected?.date}
                aria-label={`${label.weekday} ${label.dayMonth}${empty ? " (sin huecos)" : ""}`}
                onClick={() => onDateChange(day.date)}
                className={cn(
                  "grid rounded-md border px-1 py-2 text-center text-xs disabled:opacity-40",
                  day.date === selected?.date ? "bg-primary text-primary-foreground border-primary" : "hover:bg-accent",
                )}
              >
                <span className="capitalize">{label.weekday}</span>
                <span className="font-medium">{label.dayMonth}</span>
              </button>
            );
          })}
        </div>
        <Button
          variant="outline"
          size="icon"
          aria-label="Semana siguiente"
          disabled={addDays(weekStart, 7) > lastDay}
          onClick={() => onWeekChange(addDays(weekStart, 7))}
        >
          <ChevronRight aria-hidden />
        </Button>
      </div>

      {selected ? (
        <div className="grid gap-3">
          <p className="text-sm font-medium first-letter:uppercase">{formatLongDate(selected.date)}</p>
          <SlotGroup title="Mañana" slots={morning} selected={slot} onSelect={(s) => onSlotChange(s, selected.date)} />
          <SlotGroup title="Tarde" slots={afternoon} selected={slot} onSelect={(s) => onSlotChange(s, selected.date)} />
          {isOtherTimeZone(timezone) && (
            <p className="text-muted-foreground text-sm">Horas en la zona horaria del negocio ({timezone}).</p>
          )}
        </div>
      ) : (
        <p className="text-muted-foreground text-sm">No hay huecos libres esta semana. Prueba con la siguiente.</p>
      )}
    </div>
  );
}

function SlotGroup({
  title,
  slots,
  selected,
  onSelect,
}: {
  title: string;
  slots: Slot[];
  selected: Slot | null;
  onSelect: (slot: Slot) => void;
}) {
  if (slots.length === 0) return null;
  return (
    <div role="group" aria-label={title} className="grid gap-2">
      <h3 className="text-muted-foreground text-sm">{title}</h3>
      <div className="flex flex-wrap gap-2">
        {slots.map((s) => (
          <Button
            key={s.startsAt}
            type="button"
            size="sm"
            variant={s.startsAt === selected?.startsAt ? "default" : "outline"}
            aria-pressed={s.startsAt === selected?.startsAt}
            onClick={() => onSelect(s)}
          >
            {s.time}
          </Button>
        ))}
      </div>
    </div>
  );
}
