"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { CalendarCheck, ChevronLeft, ChevronRight } from "lucide-react";
import { useRef, useState } from "react";
import { useForm, type UseFormReturn } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { SelectField, TextareaField } from "@/components/forms/fields";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError } from "@/lib/api/client";
import {
  type Booking,
  HORIZON_DAYS,
  type PublicBusiness,
  type Slot,
  useBook,
  usePublicBusiness,
  useWeekAvailability,
} from "@/lib/booking";
import { addDays, formatLongDate, isOtherTimeZone, type IsoDate, shortDay, todayIn } from "@/lib/dates";
import { cn } from "@/lib/utils";
import { customerSchema, type CustomerData, type CustomerValues } from "@/lib/validation/booking";
import { formatDuration, formatPrice } from "@/lib/validation/catalog";

/** Página pública de reservas de un negocio: servicio → profesional → día y hora → datos. */
export function BookingPage({ slug }: { slug: string }) {
  const business = usePublicBusiness(slug);
  const [booking, setBooking] = useState<Booking | null>(null);

  if (business.isPending) return <p role="status">Cargando…</p>;
  if (business.isError) {
    const notFound = business.error instanceof ApiError && business.error.status === 404;
    return (
      <Alert variant="destructive">
        <AlertDescription>
          {notFound
            ? "No encontramos este negocio. Revisa la dirección."
            : "No se pudo cargar la página. Recarga para intentarlo de nuevo."}
        </AlertDescription>
      </Alert>
    );
  }
  if (booking) return <BookingConfirmation booking={booking} onAgain={() => setBooking(null)} />;
  return <BookingFlow slug={slug} business={business.data} onBooked={setBooking} />;
}

const FIELDS = ["fullName", "email", "phone", "notes"] as const;

function BookingFlow({
  slug,
  business,
  onBooked,
}: {
  slug: string;
  business: PublicBusiness;
  onBooked: (booking: Booking) => void;
}) {
  const today = todayIn(business.timezone);
  const [serviceId, setServiceId] = useState<string | null>(null);
  const [staffId, setStaffId] = useState<string>(""); // "" = cualquier profesional
  const [weekStart, setWeekStart] = useState<IsoDate>(today);
  const [date, setDate] = useState<IsoDate | null>(null);
  const [slot, setSlot] = useState<Slot | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const timeSection = useRef<HTMLElement>(null);

  // El formulario vive aquí y no en su sección: si el hueco se ocupa y hay que elegir otra hora,
  // lo que el cliente ya había escrito no se pierde.
  const form = useForm<CustomerValues, unknown, CustomerData>({
    resolver: zodResolver(customerSchema),
    defaultValues: { fullName: "", email: "", phone: "", notes: "" },
  });

  const service = business.services.find((s) => s.id === serviceId) ?? null;
  const staffForService = business.staff.filter((s) => serviceId && s.serviceIds.includes(serviceId));

  const resetTime = () => {
    setSlot(null);
    setConflict(null);
  };

  if (business.services.length === 0) {
    return (
      <>
        <BusinessHeader business={business} />
        <Alert>
          <AlertDescription>Este negocio todavía no admite reservas online.</AlertDescription>
        </Alert>
      </>
    );
  }

  return (
    <>
      <BusinessHeader business={business} />

      <section aria-labelledby="step-service" className="grid gap-3">
        <h2 id="step-service" className="text-lg font-semibold">
          1. Elige un servicio
        </h2>
        <div role="radiogroup" aria-labelledby="step-service" className="grid gap-2">
          {business.services.map((s) => (
            <label
              key={s.id}
              className={cn(
                "hover:bg-accent flex cursor-pointer items-start gap-3 rounded-lg border p-4",
                s.id === serviceId && "border-primary ring-primary/30 ring-2",
              )}
            >
              <input
                type="radio"
                name="service"
                value={s.id}
                checked={s.id === serviceId}
                onChange={() => {
                  setServiceId(s.id);
                  setStaffId("");
                  setDate(null);
                  resetTime();
                }}
                className="mt-1"
              />
              <span className="grid gap-1">
                <span className="font-medium">{s.name}</span>
                {s.description && <span className="text-muted-foreground text-sm">{s.description}</span>}
                <span className="text-muted-foreground text-sm">
                  {formatDuration(s.durationMin)}
                  {s.priceCents != null && ` · ${formatPrice(s.priceCents)}`}
                </span>
              </span>
            </label>
          ))}
        </div>
      </section>

      {service && (
        <section aria-labelledby="step-staff" className="grid gap-3">
          <h2 id="step-staff" className="text-lg font-semibold">
            2. ¿Con quién?
          </h2>
          <SelectField
            label="Profesional"
            value={staffId}
            onChange={(e) => {
              setStaffId(e.target.value);
              resetTime();
            }}
          >
            <option value="">Cualquier profesional</option>
            {staffForService.map((s) => (
              <option key={s.id} value={s.id}>
                {s.displayName}
              </option>
            ))}
          </SelectField>
        </section>
      )}

      {service && (
        <section ref={timeSection} aria-labelledby="step-time" className="grid gap-3" tabIndex={-1}>
          <h2 id="step-time" className="text-lg font-semibold">
            3. Elige día y hora
          </h2>
          {conflict && (
            <Alert variant="destructive">
              <AlertDescription>{conflict}</AlertDescription>
            </Alert>
          )}
          <TimePicker
            slug={slug}
            serviceId={service.id}
            staffId={staffId || null}
            timezone={business.timezone}
            today={today}
            weekStart={weekStart}
            onWeekChange={(start) => {
              setWeekStart(start);
              setDate(null);
              resetTime();
            }}
            date={date}
            onDateChange={(d) => {
              setDate(d);
              resetTime();
            }}
            slot={slot}
            onSlotChange={(s, day) => {
              setDate(day);
              setSlot(s);
              setConflict(null);
            }}
          />
        </section>
      )}

      {service && slot && date && (
        <CustomerSection
          slug={slug}
          form={form}
          summary={`${service.name} con ${
            staffForService.find((s) => s.id === staffId)?.displayName ?? "cualquier profesional"
          } · ${formatLongDate(date)} a las ${slot.time}`}
          request={{ serviceId: service.id, staffId: staffId || null, startsAt: slot.startsAt }}
          onBooked={onBooked}
          onSlotTaken={(message) => {
            setSlot(null);
            setConflict(message);
            timeSection.current?.focus();
            timeSection.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
          }}
        />
      )}
    </>
  );
}

function BusinessHeader({ business }: { business: PublicBusiness }) {
  return (
    <header className="grid gap-1">
      <h1 className="text-3xl font-bold tracking-tight">{business.name}</h1>
      <p className="text-muted-foreground">Reserva tu cita online.</p>
    </header>
  );
}

function TimePicker({
  slug,
  serviceId,
  staffId,
  timezone,
  today,
  weekStart,
  onWeekChange,
  date,
  onDateChange,
  slot,
  onSlotChange,
}: {
  slug: string;
  serviceId: string;
  staffId: string | null;
  timezone: string;
  today: IsoDate;
  weekStart: IsoDate;
  onWeekChange: (start: IsoDate) => void;
  date: IsoDate | null;
  onDateChange: (date: IsoDate) => void;
  slot: Slot | null;
  onSlotChange: (slot: Slot, date: IsoDate) => void;
}) {
  const weekEnd = addDays(weekStart, 6);
  const availability = useWeekAvailability(slug, serviceId, staffId, weekStart, weekEnd);
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

function CustomerSection({
  slug,
  form,
  summary,
  request,
  onBooked,
  onSlotTaken,
}: {
  slug: string;
  form: UseFormReturn<CustomerValues, unknown, CustomerData>;
  summary: string;
  request: { serviceId: string; staffId: string | null; startsAt: string };
  onBooked: (booking: Booking) => void;
  onSlotTaken: (message: string) => void;
}) {
  const book = useBook(slug);
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = form;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      onBooked(
        await book.mutateAsync({
          ...request,
          customer: { fullName: values.fullName, email: values.email, phone: values.phone || null },
          notes: values.notes || null,
        }),
      );
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        onSlotTaken(error.problem.detail ?? "Esa hora ya no está disponible. Elige otra.");
        return;
      }
      setFormError(
        applyServerErrors(error, FIELDS, setError, {
          "customer.fullName": "fullName",
          "customer.email": "email",
          "customer.phone": "phone",
        }),
      );
    }
  });

  return (
    <section aria-labelledby="step-customer" className="grid gap-3">
      <h2 id="step-customer" className="text-lg font-semibold">
        4. Tus datos
      </h2>
      <p className="bg-muted rounded-md p-3 text-sm first-letter:uppercase">{summary}</p>
      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <FormField label="Nombre" autoComplete="name" error={errors.fullName?.message} {...register("fullName")} />
        <FormField
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register("email")}
        />
        <FormField
          label="Teléfono (opcional)"
          type="tel"
          autoComplete="tel"
          error={errors.phone?.message}
          {...register("phone")}
        />
        <TextareaField
          label="Comentarios (opcional)"
          rows={3}
          error={errors.notes?.message}
          {...register("notes")}
        />
        {formError && (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        )}
        <Button type="submit" disabled={book.isPending}>
          {book.isPending ? "Reservando…" : "Confirmar reserva"}
        </Button>
      </form>
    </section>
  );
}

function BookingConfirmation({ booking, onAgain }: { booking: Booking; onAgain: () => void }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-2xl">
          <CalendarCheck className="size-6 text-green-600" aria-hidden />
          ¡Reserva confirmada!
        </CardTitle>
        <CardDescription>Te esperamos. Guarda estos datos de tu cita.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          <dt className="text-muted-foreground">Servicio</dt>
          <dd className="font-medium">{booking.serviceName}</dd>
          <dt className="text-muted-foreground">Profesional</dt>
          <dd className="font-medium">{booking.staffName}</dd>
          <dt className="text-muted-foreground">Día</dt>
          <dd className="font-medium first-letter:uppercase">{formatLongDate(booking.date)}</dd>
          <dt className="text-muted-foreground">Hora</dt>
          <dd className="font-medium">
            {booking.time}
            {isOtherTimeZone(booking.timezone) && ` (${booking.timezone})`}
          </dd>
          <dt className="text-muted-foreground">Email</dt>
          <dd className="font-medium">{booking.customerEmail}</dd>
        </dl>
        <Button variant="outline" onClick={onAgain} className="justify-self-start">
          Hacer otra reserva
        </Button>
      </CardContent>
    </Card>
  );
}
