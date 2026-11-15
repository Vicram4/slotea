"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { type Availability, HORIZON_DAYS, type Slot } from "@/lib/booking";
import { addDays, formatLongDate, formatMonthSpan, isOtherTimeZone, type IsoDate, shortDay } from "@/lib/dates";
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
    <div className="grid gap-5">
      <div className="grid gap-3">
        <div className="flex items-center justify-between gap-2">
          <p className="text-sm font-medium first-letter:uppercase">
            {formatMonthSpan(weekStart, addDays(weekStart, 6))}
          </p>
          <div className="flex gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Semana anterior"
              disabled={weekStart <= today}
              onClick={() => onWeekChange(addDays(weekStart, -7) < today ? today : addDays(weekStart, -7))}
            >
              <ChevronLeft aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label="Semana siguiente"
              disabled={addDays(weekStart, 7) > lastDay}
              onClick={() => onWeekChange(addDays(weekStart, 7))}
            >
              <ChevronRight aria-hidden />
            </Button>
          </div>
        </div>
        <div className="grid grid-cols-7 gap-1" aria-busy={availability.isFetching}>
          {days.map((day) => {
            const label = shortDay(day.date);
            const empty = day.slots.length === 0;
            const isSelected = day.date === selected?.date;
            return (
              <button
                key={day.date}
                type="button"
                disabled={empty}
                aria-pressed={isSelected}
                aria-label={`${label.weekday} ${label.dayMonth}${empty ? " (sin huecos)" : ""}`}
                onClick={() => onDateChange(day.date)}
                className={cn(
                  "focus-visible:ring-ring/50 grid gap-0.5 rounded-md py-2 text-center outline-none focus-visible:ring-[3px]",
                  isSelected
                    ? "bg-primary text-primary-foreground"
                    : "bg-muted hover:bg-accent disabled:text-muted-foreground disabled:bg-transparent",
                )}
              >
                <span className={cn("text-[11px] uppercase", !isSelected && "text-muted-foreground")}>
                  {label.weekday.replace(".", "")}
                </span>
                <span className={cn("text-base tabular-nums", !empty && "font-semibold")}>
                  {Number(day.date.slice(8))}
                </span>
              </button>
            );
          })}
        </div>
      </div>

      {selected ? (
        <div className="grid gap-4">
          <p className="text-sm font-medium first-letter:uppercase">{formatLongDate(selected.date)}</p>
          <SlotGroup title="Mañana" slots={morning} selected={slot} onSelect={(s) => onSlotChange(s, selected.date)} />
          <SlotGroup title="Tarde" slots={afternoon} selected={slot} onSelect={(s) => onSlotChange(s, selected.date)} />
          {isOtherTimeZone(timezone) && (
            <p className="text-muted-foreground text-sm">Horas en la zona horaria del negocio ({timezone}).</p>
          )}
        </div>
      ) : (
        <p className="text-muted-foreground text-sm">Esta semana no queda nada libre. Prueba con la siguiente.</p>
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
      <h3 className="text-muted-foreground text-xs font-medium tracking-wide uppercase">{title}</h3>
      <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
        {slots.map((s) => (
          <Button
            key={s.startsAt}
            type="button"
            className="tabular-nums"
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
