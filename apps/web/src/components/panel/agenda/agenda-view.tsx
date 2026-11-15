"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { toMinutes, useAgenda, useBusiness, type AgendaAppointment } from "@/lib/agenda";
import { useStaff } from "@/lib/catalog";
import {
  addDays,
  formatLongDate,
  formatWeekRange,
  isIsoDate,
  minutesNowIn,
  shortDay,
  startOfWeek,
  todayIn,
  type IsoDate,
} from "@/lib/dates";
import { useMe } from "@/lib/session";
import { cn } from "@/lib/utils";

import { AgendaList } from "./agenda-list";
import { AppointmentDialog } from "./appointment-dialog";
import { isPendingOutcome } from "./appointment-status";
import { TimeGrid, type GridColumn } from "./time-grid";

export type AgendaMode = "day" | "week";

/** Lo que se ve, tal y como va en la URL (`?date=…&view=week&staff=…`): se puede compartir y recargar. */
export type AgendaLocation = { date?: IsoDate; view: AgendaMode; staffId?: string };

/** La hora actual, renovada cada minuto (para «hoy» y la línea de «ahora»). */
function useNow(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

/**
 * La agenda del panel. El dueño ve a todo el equipo (columnas por profesional) o la semana de uno;
 * un empleado, solo lo suyo: lo decide la API, aquí solo se adapta la interfaz.
 */
export function AgendaView({
  location,
  onNavigate,
}: {
  location: AgendaLocation;
  onNavigate: (next: AgendaLocation) => void;
}) {
  const me = useMe().data;
  const business = useBusiness();

  if (business.isPending) return <p role="status">Cargando la agenda…</p>;
  if (business.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudo cargar la agenda. Recarga la página.</AlertDescription>
      </Alert>
    );
  }
  return (
    <Agenda
      timezone={business.data.timezone}
      isOwner={me?.activeBusiness?.role === "OWNER"}
      location={location}
      onNavigate={onNavigate}
    />
  );
}

function Agenda({
  timezone,
  isOwner,
  location,
  onNavigate,
}: {
  timezone: string;
  isOwner: boolean;
  location: AgendaLocation;
  onNavigate: (next: AgendaLocation) => void;
}) {
  const now = useNow();
  const today = todayIn(timezone, now);
  const nowMinutes = minutesNowIn(timezone, now);
  const { view } = location;
  const date = location.date ?? today;

  // La semana es de un profesional: el elegido o, si no, el primero del equipo.
  const staff = useStaff();
  const activeStaff = staff.data?.filter((s) => s.active) ?? [];
  const staffId = view === "week" ? (location.staffId ?? activeStaff[0]?.id) : location.staffId;

  const from = view === "day" ? date : startOfWeek(date);
  const to = view === "day" ? date : addDays(from, 6);
  const days = Array.from({ length: view === "day" ? 1 : 7 }, (_, i) => addDays(from, i));
  const agenda = useAgenda(
    isOwner && staffId ? { from, to, staffId } : { from, to },
    !(isOwner && view === "week" && staff.isPending),
  );

  const [showCancelled, setShowCancelled] = useState(false);
  const [selected, setSelected] = useState<AgendaAppointment | null>(null);

  const go = (next: Partial<AgendaLocation>) => onNavigate({ ...location, date, ...next });
  const step = view === "day" ? 1 : 7;
  const appointments = (agenda.data?.appointments ?? []).filter((a) => showCancelled || a.status !== "CANCELLED");
  const cancelledCount = (agenda.data?.appointments ?? []).filter((a) => a.status === "CANCELLED").length;

  const columns: GridColumn[] =
    view === "day"
      ? (agenda.data?.staff ?? []).map((s) => ({
          key: s.id,
          title: s.displayName,
          initial: s.displayName.trim().charAt(0).toUpperCase(),
          subtitle: s.active ? undefined : "Inactivo",
          today: date === today,
          nowMinutes: date === today ? nowMinutes : undefined,
          appointments: appointments.filter((a) => a.staffId === s.id),
        }))
      : days.map((day) => ({
          key: day,
          title: shortDay(day).weekday,
          subtitle: shortDay(day).dayMonth,
          today: day === today,
          nowMinutes: day === today ? nowMinutes : undefined,
          appointments: appointments.filter((a) => a.date === day),
        }));
  const noStaff = agenda.isSuccess && agenda.data.staff.length === 0 && !(isOwner && view === "week" && staffId);

  // Lo que se lee de un vistazo: cuántas, cuál es la siguiente (hoy) y cuántas quedan por marcar.
  const next =
    view === "day" && date === today
      ? appointments.find((a) => a.status === "CONFIRMED" && toMinutes(a.startTime) >= nowMinutes)
      : undefined;
  const pending = appointments.filter(isPendingOutcome).length;

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="grid gap-1">
          <h2 className="text-2xl font-semibold tracking-tight first-letter:uppercase" aria-live="polite">
            {view === "day" ? formatLongDate(date) : formatWeekRange(from)}
          </h2>
          <p className="text-muted-foreground min-h-5 text-sm">
            {agenda.isSuccess && !noStaff && (
              <>
                <span>
                  {appointments.length === 0
                    ? view === "day"
                      ? "No hay citas este día."
                      : "No hay citas esta semana."
                    : `${appointments.length} ${appointments.length === 1 ? "cita" : "citas"}`}
                </span>
                {next && (
                  <span>
                    {" "}
                    · La siguiente, a las <span className="tabular-nums">{next.startTime}</span> con{" "}
                    {next.customer.fullName}
                  </span>
                )}
                {pending > 0 && (
                  <span className="text-foreground">
                    {" "}
                    · {pending === 1 ? "1 sin marcar cómo fue" : `${pending} sin marcar cómo fueron`}
                  </span>
                )}
              </>
            )}
            {agenda.isFetching && agenda.isPlaceholderData && <span role="status"> · Actualizando…</span>}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => go({ date: today })} disabled={date === today}>
            Hoy
          </Button>
          <div className="flex rounded-md border shadow-xs">
            <Button
              variant="ghost"
              size="icon-sm"
              className="rounded-r-none"
              aria-label={view === "day" ? "Día anterior" : "Semana anterior"}
              onClick={() => go({ date: addDays(date, -step) })}
            >
              <ChevronLeft aria-hidden />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              className="rounded-l-none border-l"
              aria-label={view === "day" ? "Día siguiente" : "Semana siguiente"}
              onClick={() => go({ date: addDays(date, step) })}
            >
              <ChevronRight aria-hidden />
            </Button>
          </div>
          <div role="group" aria-label="Vista" className="bg-muted inline-flex rounded-md p-0.5 text-sm">
            {(["day", "week"] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                aria-pressed={view === mode}
                onClick={() => go({ view: mode })}
                className={cn(
                  "text-foreground/70 hover:text-foreground rounded px-3 py-1",
                  view === mode && "bg-background text-foreground font-medium shadow-xs",
                )}
              >
                {mode === "day" ? "Día" : "Semana"}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3">
        {isOwner && activeStaff.length > 0 ? (
          <div role="radiogroup" aria-label="Profesional" className="flex flex-wrap gap-1.5">
            {[...(view === "day" ? [{ id: "", displayName: "Todo el equipo" }] : []), ...activeStaff].map((s) => {
              const checked = (staffId ?? "") === s.id;
              return (
                <label
                  key={s.id}
                  className={cn(
                    "cursor-pointer rounded-full border px-3 py-1 text-sm",
                    "has-[:focus-visible]:ring-ring/50 has-[:focus-visible]:ring-[3px]",
                    checked ? "bg-primary text-primary-foreground border-primary" : "hover:bg-muted/60",
                  )}
                >
                  <input
                    type="radio"
                    name="agenda-staff"
                    value={s.id}
                    checked={checked}
                    onChange={() => go({ staffId: s.id || undefined })}
                    className="sr-only"
                  />
                  {s.displayName}
                </label>
              );
            })}
          </div>
        ) : (
          <span />
        )}
        <div className="flex flex-wrap items-center gap-4 text-sm">
          <input
            type="date"
            aria-label="Ir a la fecha"
            value={date}
            onChange={(e) => isIsoDate(e.target.value) && go({ date: e.target.value })}
            className="border-input h-8 rounded-md border bg-transparent px-2 shadow-xs"
          />
          <div className="flex items-center gap-2">
            <Switch id="agenda-cancelled" checked={showCancelled} onCheckedChange={setShowCancelled} />
            <Label htmlFor="agenda-cancelled" className="font-normal">
              Mostrar canceladas{cancelledCount > 0 && ` (${cancelledCount})`}
            </Label>
          </div>
        </div>
      </div>

      {agenda.isPending && <p role="status">Cargando citas…</p>}
      {agenda.isError && (
        <Alert variant="destructive">
          <AlertDescription>No se pudieron cargar las citas. Lo volveremos a intentar en un momento.</AlertDescription>
        </Alert>
      )}
      {noStaff &&
        (isOwner ? (
          <Alert>
            <AlertDescription>
              Aún no hay profesionales. Añádelos en{" "}
              <Link href="/panel/profesionales" className="underline underline-offset-4">
                Profesionales
              </Link>{" "}
              para empezar a recibir reservas.
            </AlertDescription>
          </Alert>
        ) : (
          <Alert>
            <AlertDescription>
              Tu usuario aún no está enlazado a una ficha de profesional. Pide al dueño del negocio que lo haga en
              «Profesionales» para ver aquí tus citas.
            </AlertDescription>
          </Alert>
        ))}

      {agenda.isSuccess && !noStaff && (
        <>
          <div className="hidden md:block">
            <TimeGrid
              columns={columns}
              onSelect={setSelected}
              label={view === "day" ? "Agenda del día" : "Agenda de la semana"}
            />
          </div>
          <div className="md:hidden">
            <AgendaList
              days={days}
              appointments={appointments}
              showStaff={view === "day" && agenda.data.staff.length > 1}
              onSelect={setSelected}
            />
          </div>
        </>
      )}

      <AppointmentDialog
        appointment={selected}
        timezone={timezone}
        onClose={() => setSelected(null)}
        onChanged={setSelected}
      />
    </div>
  );
}
