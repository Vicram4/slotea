"use client";

import { Copy, Plus, X } from "lucide-react";
import { useState, type FormEvent } from "react";

import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { type TimeSlot, useCanEdit, useSaveWeeklyHours, useWeeklyHours } from "@/lib/catalog";
import { DAYS, slotErrors } from "@/lib/validation/catalog";

type EditableSlot = TimeSlot & { key: number };

let nextKey = 0;
const withKey = (slot: TimeSlot): EditableSlot => ({ ...slot, key: nextKey++ });

/** Horario semanal de un profesional, en hora local del negocio. Varios tramos al día = jornada partida. */
export function WeeklyHoursEditor({ staffId }: { staffId: string }) {
  const hours = useWeeklyHours(staffId);
  const canEdit = useCanEdit();

  if (hours.isPending) return <p role="status">Cargando horario…</p>;
  if (hours.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudo cargar el horario.</AlertDescription>
      </Alert>
    );
  }
  if (!canEdit) return <WeeklyHoursSummary slots={hours.data.slots} />;
  return <Editor staffId={staffId} initial={hours.data.slots} />;
}

function WeeklyHoursSummary({ slots }: { slots: TimeSlot[] }) {
  return (
    <dl className="grid grid-cols-[8rem_1fr] gap-y-2 text-sm">
      {DAYS.map(({ day, name }) => {
        const daySlots = slots.filter((s) => s.dayOfWeek === day);
        return (
          <div key={day} className="contents">
            <dt className="text-muted-foreground">{name}</dt>
            <dd>{daySlots.length ? daySlots.map((s) => `${s.start}–${s.end}`).join(", ") : "Cerrado"}</dd>
          </div>
        );
      })}
    </dl>
  );
}

function Editor({ staffId, initial }: { staffId: string; initial: TimeSlot[] }) {
  const save = useSaveWeeklyHours(staffId);
  const [slots, setSlots] = useState<EditableSlot[]>(() => initial.map(withKey));
  const [submitted, setSubmitted] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const errors = submitted ? slotErrors(slots) : new Map<number, string>();

  const change = (next: EditableSlot[]) => {
    save.reset();
    setFormError(null);
    setSlots(next);
  };
  const update = (key: number, patch: Partial<TimeSlot>) =>
    change(slots.map((s) => (s.key === key ? { ...s, ...patch } : s)));
  const add = (day: number) => {
    const last = slots.filter((s) => s.dayOfWeek === day).at(-1);
    // Propuesta: la mañana; si ya hay un tramo, la tarde.
    change([...slots, withKey(last ? { dayOfWeek: day, start: "16:00", end: "20:00" } : { dayOfWeek: day, start: "09:00", end: "14:00" })]);
  };
  const remove = (key: number) => change(slots.filter((s) => s.key !== key));
  const copyMondayToWeekdays = () => {
    const monday = slots.filter((s) => s.dayOfWeek === 1);
    const others = slots.filter((s) => s.dayOfWeek === 1 || s.dayOfWeek > 5);
    const copies = [2, 3, 4, 5].flatMap((day) => monday.map((s) => withKey({ ...s, dayOfWeek: day })));
    change([...others, ...copies]);
  };

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    if (slotErrors(slots).size > 0) return;
    try {
      await save.mutateAsync(slots.map(({ dayOfWeek, start, end }) => ({ dayOfWeek, start, end })));
      setSubmitted(false);
    } catch (error) {
      setFormError(applyServerErrors(error, [], () => undefined) ?? "Revisa los tramos del horario.");
    }
  };

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <div className="grid gap-3">
        {DAYS.map(({ day, name }) => {
          const daySlots = slots.filter((s) => s.dayOfWeek === day);
          return (
            <fieldset key={day} className="grid gap-2 rounded-lg border p-3 sm:grid-cols-[7rem_1fr]">
              <legend className="sr-only">{name}</legend>
              <span aria-hidden className="pt-2 text-sm font-medium">
                {name}
              </span>
              <div className="grid gap-2">
                {daySlots.length === 0 && <span className="text-muted-foreground pt-2 text-sm">Cerrado</span>}
                {daySlots.map((slot, i) => {
                  const index = slots.indexOf(slot);
                  const error = errors.get(index);
                  const label = `${name}, tramo ${i + 1}`;
                  const errorId = `slot-${slot.key}-error`;
                  return (
                    <div key={slot.key} className="grid gap-1">
                      <div className="flex items-center gap-2">
                        <Input
                          type="time"
                          step={300}
                          aria-label={`${label}: inicio`}
                          aria-invalid={error ? true : undefined}
                          aria-describedby={error ? errorId : undefined}
                          value={slot.start}
                          onChange={(e) => update(slot.key, { start: e.target.value })}
                          className="w-32"
                        />
                        <span aria-hidden>–</span>
                        <Input
                          type="time"
                          step={300}
                          aria-label={`${label}: fin`}
                          aria-invalid={error ? true : undefined}
                          aria-describedby={error ? errorId : undefined}
                          value={slot.end}
                          onChange={(e) => update(slot.key, { end: e.target.value })}
                          className="w-32"
                        />
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          aria-label={`Quitar ${label}`}
                          onClick={() => remove(slot.key)}
                        >
                          <X aria-hidden />
                        </Button>
                      </div>
                      {error && (
                        <p id={errorId} className="text-destructive text-sm">
                          {error}
                        </p>
                      )}
                    </div>
                  );
                })}
                <Button
                  type="button"
                  variant="link"
                  size="sm"
                  className="justify-self-start px-0"
                  aria-label={`Añadir tramo el ${name.toLowerCase()}`}
                  onClick={() => add(day)}
                >
                  <Plus aria-hidden /> Añadir tramo
                </Button>
              </div>
            </fieldset>
          );
        })}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button type="button" variant="outline" onClick={copyMondayToWeekdays}>
          <Copy aria-hidden /> Copiar el lunes de martes a viernes
        </Button>
        <div className="flex items-center gap-3">
          {save.isSuccess && (
            <span role="status" className="text-muted-foreground text-sm">
              Horario guardado.
            </span>
          )}
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? "Guardando…" : "Guardar horario"}
          </Button>
        </div>
      </div>
    </form>
  );
}
