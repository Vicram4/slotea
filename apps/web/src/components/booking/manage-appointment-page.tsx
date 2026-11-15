"use client";

import { CalendarClock, CalendarDays, CalendarX, Clock, IdCard, User } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
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
import { addDays, formatLongDate, isOtherTimeZone, type IsoDate, shortDay, todayIn } from "@/lib/dates";

import { PublicSheet, StaffChoice, StepHeading, SummaryItem, SummaryList } from "./public-sheet";
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
    <PublicSheet
      business={business.data}
      tagline="Tu cita. Cámbiala o cancélala desde aquí, sin llamar."
      aside={<AppointmentSummary appointment={data} />}
    >
      {notice && (
        <Alert role="status">
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}

      {data.changeable ? (
        <ChangeActions
          slug={slug}
          token={token}
          business={business.data}
          appointment={data}
          onDone={(message) => setNotice(message)}
        />
      ) : (
        <ClosedAppointment slug={slug} business={business.data} appointment={data} />
      )}
    </PublicSheet>
  );
}

function AppointmentSummary({ appointment }: { appointment: ManagedAppointment }) {
  const status = STATUS[appointment.status];
  const struck = appointment.status === "CANCELLED" ? "line-through" : undefined;
  return (
    <SummaryList label="Tu cita">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <dt className="sr-only">Servicio</dt>
        <dd className="text-base font-medium">{appointment.serviceName}</dd>
        <dt className="sr-only">Estado</dt>
        <dd>
          <Badge variant={status.variant}>{status.label}</Badge>
        </dd>
      </div>
      <SummaryItem icon={CalendarDays} label="Día" className={struck}>
        {formatLongDate(appointment.date)}
      </SummaryItem>
      <SummaryItem icon={Clock} label="Hora" className={struck}>
        {appointment.time} – {appointment.endTime}
        {isOtherTimeZone(appointment.timezone) && ` (${appointment.timezone})`}
      </SummaryItem>
      <SummaryItem icon={User} label="Profesional">
        {appointment.staffName}
      </SummaryItem>
      <SummaryItem icon={IdCard} label="A nombre de">
        {appointment.customerName}
      </SummaryItem>
    </SummaryList>
  );
}

/** Lo que queda cuando ya no se puede tocar: por qué, y qué hacer. */
function ClosedAppointment({
  slug,
  business,
  appointment,
}: {
  slug: string;
  business: PublicBusiness;
  appointment: ManagedAppointment;
}) {
  if (appointment.status === "CONFIRMED") {
    return (
      <section aria-labelledby="closed-title" className="grid gap-2">
        <StepHeading id="closed-title">Ya casi es la hora</StepHeading>
        <p className="text-muted-foreground text-sm">
          Falta poco para tu cita: ya no se puede cambiar ni cancelar desde aquí. Si lo necesitas, contacta con{" "}
          {business.name}.
        </p>
      </section>
    );
  }
  const cancelled = appointment.status === "CANCELLED";
  return (
    <section aria-labelledby="closed-title" className="grid gap-4">
      <StepHeading
        id="closed-title"
        hint={cancelled ? "El hueco ya está libre para otra persona." : "¿Te reservamos la siguiente?"}
      >
        {cancelled ? "Esta cita está cancelada" : "Esta cita ya pasó"}
      </StepHeading>
      <Button asChild className="justify-self-start">
        <Link href={`/${slug}`}>Reservar otra cita</Link>
      </Button>
    </section>
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
      <section aria-labelledby="actions-title" className="grid gap-4">
        <StepHeading id="actions-title" hint="Cambia la hora o cancela en un momento. No hace falta llamar.">
          ¿Te ha surgido algo?
        </StepHeading>
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
      </section>

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
  const staffOptions = currentMissing
    ? [{ id: appointment.staffId, displayName: appointment.staffName }, ...staffForService]
    : staffForService;

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
    <section ref={section} aria-labelledby="reschedule-title" className="grid gap-6 outline-none" tabIndex={-1}>
      <StepHeading id="reschedule-title" hint="Tu hora actual sigue siendo tuya hasta que confirmes la nueva.">
        ¿Cuándo te viene mejor?
      </StepHeading>
      {/* Con una sola persona que haga el servicio, no hay nada que elegir. */}
      {staffOptions.length > 1 && (
        <div className="grid gap-2">
          <p id="reschedule-staff" className="text-sm font-medium">
            ¿Con quién?
          </p>
          <StaffChoice
            labelledBy="reschedule-staff"
            staff={staffOptions}
            value={staffId}
            onChange={(id) => {
              setStaffId(id);
              setSlot(null);
            }}
          />
        </div>
      )}
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
        <Button size="lg" onClick={onConfirm} disabled={reschedule.isPending}>
          {reschedule.isPending
            ? "Cambiando…"
            : `Cambiar a: ${shortDay(date).weekday} ${shortDay(date).dayMonth}, ${slot.time}`}
        </Button>
      )}
    </section>
  );
}
