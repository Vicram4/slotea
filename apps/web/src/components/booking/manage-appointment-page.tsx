"use client";

import { CalendarClock, CalendarX } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";

import { SelectField } from "@/components/forms/fields";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { ApiError } from "@/lib/api/client";
import {
  type ManagedAppointment,
  type PublicBusiness,
  type Slot,
  useCancelAppointment,
  useManageAvailability,
  useManagedAppointment,
  usePublicBusiness,
  useRescheduleAppointment,
} from "@/lib/booking";
import { useBrandColor } from "@/lib/brand";
import { addDays, formatLongDate, isOtherTimeZone, type IsoDate, todayIn } from "@/lib/dates";

import { TimePicker } from "./time-picker";

const STATUS: Record<ManagedAppointment["status"], { label: string; variant: "default" | "secondary" | "destructive" }> =
  {
    CONFIRMED: { label: "Confirmada", variant: "default" },
    CANCELLED: { label: "Cancelada", variant: "destructive" },
    COMPLETED: { label: "Completada", variant: "secondary" },
    NO_SHOW: { label: "No presentada", variant: "secondary" },
  };

/**
 * La página del enlace del email: ver la cita, cancelarla o cambiarla de hora, sin cuenta.
 * Cancelar pide confirmación y es un POST: los antivirus del correo abren los enlaces, y abrir la
 * página nunca cambia nada.
 */
export function ManageAppointmentPage({ slug, token }: { slug: string; token: string }) {
  const business = usePublicBusiness(slug);
  const appointment = useManagedAppointment(slug, token);
  const [notice, setNotice] = useState<string | null>(null);
  useBrandColor(business.data?.brandColor);

  if (business.isPending || appointment.isPending) return <p role="status">Cargando…</p>;
  if (business.isError || appointment.isError) {
    const error = appointment.error ?? business.error;
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <Alert variant="destructive">
        <AlertDescription>
          {notFound
            ? "Este enlace no es válido o la cita ya no existe. Revisa que lo has copiado entero."
            : "No se pudo cargar tu cita. Recarga para intentarlo de nuevo."}
        </AlertDescription>
      </Alert>
    );
  }

  const data = appointment.data;
  return (
    <>
      <header className="grid gap-1">
        <h1 className="text-3xl font-bold tracking-tight">{business.data.name}</h1>
        <p className="text-muted-foreground">Tu cita</p>
      </header>

      {notice && (
        <Alert role="status">
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}

      <AppointmentCard appointment={data} />

      {data.status === "CANCELLED" && (
        <Button asChild className="justify-self-start">
          <Link href={`/${slug}`}>Reservar otra cita</Link>
        </Button>
      )}
      {data.status === "CONFIRMED" && !data.changeable && (
        <Alert>
          <AlertDescription>
            Falta poco para tu cita: ya no se puede cambiar ni cancelar desde aquí. Si lo necesitas, contacta con{" "}
            {business.data.name}.
          </AlertDescription>
        </Alert>
      )}
      {data.changeable && (
        <ChangeActions
          slug={slug}
          token={token}
          business={business.data}
          appointment={data}
          onDone={(message) => setNotice(message)}
        />
      )}
    </>
  );
}

function AppointmentCard({ appointment }: { appointment: ManagedAppointment }) {
  const status = STATUS[appointment.status];
  const cancelled = appointment.status === "CANCELLED";
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <CardTitle className="text-xl">{appointment.serviceName}</CardTitle>
        <Badge variant={status.variant}>{status.label}</Badge>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          <dt className="text-muted-foreground">A nombre de</dt>
          <dd className="font-medium">{appointment.customerName}</dd>
          <dt className="text-muted-foreground">Profesional</dt>
          <dd className="font-medium">{appointment.staffName}</dd>
          <dt className="text-muted-foreground">Día</dt>
          <dd className={cancelled ? "line-through" : "font-medium first-letter:uppercase"}>
            {formatLongDate(appointment.date)}
          </dd>
          <dt className="text-muted-foreground">Hora</dt>
          <dd className={cancelled ? "line-through" : "font-medium"}>
            {appointment.time} – {appointment.endTime}
            {isOtherTimeZone(appointment.timezone) && ` (${appointment.timezone})`}
          </dd>
        </dl>
      </CardContent>
    </Card>
  );
}

function ChangeActions({
  slug,
  token,
  business,
  appointment,
  onDone,
}: {
  slug: string;
  token: string;
  business: PublicBusiness;
  appointment: ManagedAppointment;
  onDone: (message: string) => void;
}) {
  const [rescheduling, setRescheduling] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const cancel = useCancelAppointment(slug, token);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const onCancel = async () => {
    setCancelError(null);
    try {
      await cancel.mutateAsync();
      setConfirmCancel(false);
      onDone("Tu cita está cancelada. Te hemos enviado un email de confirmación.");
    } catch (error) {
      setCancelError(
        error instanceof ApiError && error.problem.detail
          ? error.problem.detail
          : "No se pudo cancelar la cita. Inténtalo de nuevo.",
      );
    }
  };

  return (
    <>
      <div className="flex flex-wrap gap-2">
        <Button variant={rescheduling ? "secondary" : "default"} onClick={() => setRescheduling((r) => !r)}>
          <CalendarClock aria-hidden />
          {rescheduling ? "No cambiar la hora" : "Cambiar hora"}
        </Button>
        <Button variant="outline" onClick={() => setConfirmCancel(true)}>
          <CalendarX aria-hidden />
          Cancelar cita
        </Button>
      </div>

      {rescheduling && (
        <Reschedule
          slug={slug}
          token={token}
          business={business}
          appointment={appointment}
          onRescheduled={() => {
            setRescheduling(false);
            onDone("Hemos cambiado tu cita. Te hemos enviado un email con la nueva hora.");
          }}
        />
      )}

      <Dialog open={confirmCancel} onOpenChange={setConfirmCancel}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>¿Cancelar la cita?</DialogTitle>
            <DialogDescription>
              {appointment.serviceName}, {formatLongDate(appointment.date)} a las {appointment.time}. El hueco quedará
              libre para otra persona.
            </DialogDescription>
          </DialogHeader>
          {cancelError && (
            <Alert variant="destructive">
              <AlertDescription>{cancelError}</AlertDescription>
            </Alert>
          )}
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">Mantener la cita</Button>
            </DialogClose>
            <Button variant="destructive" onClick={onCancel} disabled={cancel.isPending}>
              {cancel.isPending ? "Cancelando…" : "Sí, cancelar"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function Reschedule({
  slug,
  token,
  business,
  appointment,
  onRescheduled,
}: {
  slug: string;
  token: string;
  business: PublicBusiness;
  appointment: ManagedAppointment;
  onRescheduled: () => void;
}) {
  const today = todayIn(business.timezone);
  const [staffId, setStaffId] = useState<string>(appointment.staffId); // "" = cualquier profesional
  const [weekStart, setWeekStart] = useState<IsoDate>(appointment.date < today ? today : appointment.date);
  const [date, setDate] = useState<IsoDate | null>(null);
  const [slot, setSlot] = useState<Slot | null>(null);
  const [error, setError] = useState<string | null>(null);
  const section = useRef<HTMLElement>(null);
  const availability = useManageAvailability(slug, token, staffId || null, weekStart, addDays(weekStart, 6), true);
  const reschedule = useRescheduleAppointment(slug, token);

  const staffForService = business.staff.filter((s) => s.serviceIds.includes(appointment.serviceId));
  // Si el profesional ya no está disponible para reservas, que pueda elegir otro sin perderlo de la lista.
  const currentMissing = !staffForService.some((s) => s.id === appointment.staffId);

  const onConfirm = async () => {
    if (!slot) return;
    setError(null);
    try {
      await reschedule.mutateAsync({ startsAt: slot.startsAt, staffId: staffId || null });
      onRescheduled();
    } catch (e) {
      setSlot(null);
      setError(
        e instanceof ApiError && e.problem.detail ? e.problem.detail : "No se pudo cambiar la cita. Inténtalo de nuevo.",
      );
      section.current?.focus();
    }
  };

  return (
    <section ref={section} aria-labelledby="reschedule-title" className="grid gap-4" tabIndex={-1}>
      <h2 id="reschedule-title" className="text-lg font-semibold">
        Elige la nueva hora
      </h2>
      <SelectField
        label="Profesional"
        value={staffId}
        onChange={(e) => {
          setStaffId(e.target.value);
          setSlot(null);
        }}
      >
        <option value="">Cualquier profesional</option>
        {currentMissing && <option value={appointment.staffId}>{appointment.staffName}</option>}
        {staffForService.map((s) => (
          <option key={s.id} value={s.id}>
            {s.displayName}
          </option>
        ))}
      </SelectField>
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      <TimePicker
        availability={availability}
        timezone={business.timezone}
        today={today}
        weekStart={weekStart}
        onWeekChange={(start) => {
          setWeekStart(start);
          setDate(null);
          setSlot(null);
        }}
        date={date}
        onDateChange={(d) => {
          setDate(d);
          setSlot(null);
        }}
        slot={slot}
        onSlotChange={(s, day) => {
          setDate(day);
          setSlot(s);
          setError(null);
        }}
      />
      {slot && date && (
        <div className="grid gap-3">
          <p className="bg-muted rounded-md p-3 text-sm first-letter:uppercase">
            {formatLongDate(date)} a las {slot.time}
          </p>
          <Button onClick={onConfirm} disabled={reschedule.isPending} className="justify-self-start">
            {reschedule.isPending ? "Cambiando…" : "Confirmar el cambio"}
          </Button>
        </div>
      )}
    </section>
  );
}
