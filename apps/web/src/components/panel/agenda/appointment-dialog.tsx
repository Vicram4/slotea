"use client";

import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  statusLabels,
  useCancelAppointment,
  useRecordOutcome,
  type AgendaAppointment,
  type Outcome,
} from "@/lib/agenda";
import { ApiError } from "@/lib/api/client";
import { formatLongDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

import { statusBadgeStyles } from "./appointment-status";

/** «15 nov, 12:03» en la zona del negocio. */
function formatInstant(iso: string, timeZone: string): string {
  return new Intl.DateTimeFormat("es-ES", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    timeZone,
  }).format(new Date(iso));
}

function errorMessage(error: unknown): string {
  // Los 409 de la API ya explican qué ha pasado («La cita ya ha empezado…»).
  if (error instanceof ApiError && error.problem.detail) return error.problem.detail;
  return "No se pudo guardar el cambio. Inténtalo de nuevo.";
}

/**
 * Detalle de una cita y lo que se puede hacer con ella: marcar cómo fue (si ya ha empezado) o
 * cancelarla (si aún no), con confirmación porque el cliente recibe un email.
 */
export function AppointmentDialog({
  appointment,
  timezone,
  onClose,
  onChanged,
}: {
  appointment: AgendaAppointment | null;
  timezone: string;
  onClose: () => void;
  onChanged: (appointment: AgendaAppointment) => void;
}) {
  return (
    <Dialog open={appointment !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        {/* key: al abrir otra cita, el estado interno (confirmación, errores) empieza de cero. */}
        {appointment && (
          <Details key={appointment.id} appointment={appointment} timezone={timezone} onChanged={onChanged} />
        )}
      </DialogContent>
    </Dialog>
  );
}

function Details({
  appointment: a,
  timezone,
  onChanged,
}: {
  appointment: AgendaAppointment;
  timezone: string;
  onChanged: (appointment: AgendaAppointment) => void;
}) {
  const outcome = useRecordOutcome();
  const cancel = useCancelAppointment();
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const busy = outcome.isPending || cancel.isPending;
  const error = outcome.error ?? cancel.error;

  const record = (status: Outcome) =>
    outcome.mutate({ id: a.id, status }, { onSuccess: (updated) => onChanged(updated) });
  const confirmCancel = () =>
    cancel.mutate(a.id, {
      onSuccess: (updated) => {
        setConfirmingCancel(false);
        onChanged(updated);
      },
    });

  return (
    <>
      <DialogHeader>
        <DialogTitle>{a.customer.fullName}</DialogTitle>
        <DialogDescription>
          {a.serviceName} con {a.staffName}
        </DialogDescription>
      </DialogHeader>

      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
        <dt className="text-muted-foreground">Estado</dt>
        <dd>
          <span className={cn("rounded-full px-2 py-0.5 text-xs font-medium", statusBadgeStyles[a.status])}>
            {statusLabels[a.status]}
          </span>
        </dd>
        <dt className="text-muted-foreground">Día</dt>
        <dd className="first-letter:uppercase">{formatLongDate(a.date)}</dd>
        <dt className="text-muted-foreground">Hora</dt>
        <dd className="tabular-nums">
          {a.startTime} – {a.endTime}
        </dd>
        <dt className="text-muted-foreground">Email</dt>
        <dd>
          <a href={`mailto:${a.customer.email}`} className="underline-offset-4 hover:underline">
            {a.customer.email}
          </a>
        </dd>
        <dt className="text-muted-foreground">Teléfono</dt>
        <dd>
          {a.customer.phone ? (
            <a href={`tel:${a.customer.phone.replace(/\s/g, "")}`} className="underline-offset-4 hover:underline">
              {a.customer.phone}
            </a>
          ) : (
            "—"
          )}
        </dd>
        {a.notes && (
          <>
            <dt className="text-muted-foreground">Notas</dt>
            <dd className="whitespace-pre-line">{a.notes}</dd>
          </>
        )}
        <dt className="text-muted-foreground">Reservada</dt>
        <dd>{formatInstant(a.createdAt, timezone)}</dd>
        {a.cancelledAt && (
          <>
            <dt className="text-muted-foreground">Cancelada</dt>
            <dd>{formatInstant(a.cancelledAt, timezone)}</dd>
          </>
        )}
      </dl>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(error)}</AlertDescription>
        </Alert>
      )}

      {a.canRecordOutcome && (
        <div className="grid gap-2 border-t pt-4">
          <p className="text-sm font-medium">¿Cómo fue la cita?</p>
          <div className="flex flex-wrap gap-2">
            <Button size="sm" disabled={busy || a.status === "COMPLETED"} onClick={() => record("COMPLETED")}>
              Completada
            </Button>
            <Button
              size="sm"
              variant="outline"
              disabled={busy || a.status === "NO_SHOW"}
              onClick={() => record("NO_SHOW")}
            >
              No se presentó
            </Button>
            {a.status !== "CONFIRMED" && (
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => record("CONFIRMED")}>
                Deshacer
              </Button>
            )}
          </div>
        </div>
      )}

      {a.canCancel && (
        <DialogFooter className="border-t pt-4">
          {confirmingCancel ? (
            <div role="group" aria-label="Confirmar cancelación" className="grid w-full gap-3">
              <p className="text-sm">
                Se avisará a {a.customer.fullName} por email. El hueco quedará libre para otra reserva.
              </p>
              <div className="flex flex-wrap justify-end gap-2">
                <Button variant="outline" size="sm" disabled={busy} onClick={() => setConfirmingCancel(false)}>
                  No, mantenerla
                </Button>
                <Button variant="destructive" size="sm" disabled={busy} onClick={confirmCancel}>
                  {cancel.isPending ? "Cancelando…" : "Sí, cancelar la cita"}
                </Button>
              </div>
            </div>
          ) : (
            <Button variant="outline" size="sm" onClick={() => setConfirmingCancel(true)}>
              Cancelar cita
            </Button>
          )}
        </DialogFooter>
      )}
    </>
  );
}
