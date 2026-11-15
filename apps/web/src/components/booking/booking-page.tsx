"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { CalendarCheck } from "lucide-react";
import Link from "next/link";
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
  manageHref,
  type PublicBusiness,
  type Slot,
  useBook,
  usePublicBusiness,
  useWeekAvailability,
} from "@/lib/booking";
import { useBrandColor } from "@/lib/brand";
import { DEMO_CUSTOMER } from "@/lib/demo";
import { addDays, formatLongDate, isOtherTimeZone, type IsoDate, todayIn } from "@/lib/dates";
import { cn } from "@/lib/utils";
import { customerSchema, type CustomerData, type CustomerValues } from "@/lib/validation/booking";
import { formatDuration, formatPrice } from "@/lib/validation/catalog";

import { TimePicker } from "./time-picker";

/** Página pública de reservas de un negocio: servicio → profesional → día y hora → datos. */
export function BookingPage({ slug }: { slug: string }) {
  const business = usePublicBusiness(slug);
  const [booking, setBooking] = useState<Booking | null>(null);
  useBrandColor(business.data?.brandColor);

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
  if (booking) {
    return (
      <BookingConfirmation
        slug={slug}
        booking={booking}
        demo={business.data.demo}
        onAgain={() => setBooking(null)}
      />
    );
  }
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
  const availability = useWeekAvailability(slug, service?.id ?? null, staffId || null, weekStart, addDays(weekStart, 6));
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
            availability={availability}
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
          demo={business.demo}
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
    <header className="grid gap-3">
      {business.demo && <DemoNotice />}
      <div className="grid gap-1">
        <h1 className="text-3xl font-bold tracking-tight">{business.name}</h1>
        <p className="text-muted-foreground">Reserva tu cita online.</p>
      </div>
    </header>
  );
}

/** En una copia de la demo: aquí se juega el papel del cliente, y los emails van a la bandeja. */
function DemoNotice() {
  return (
    <div
      role="note"
      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950"
    >
      <p>
        <strong>Demo:</strong> estás reservando como cliente. No se envía ningún email: lo verás en la bandeja de la
        demo.
      </p>
      <div className="flex gap-2">
        <Button size="sm" variant="outline" asChild>
          <Link href="/panel/bandeja">Bandeja de la demo</Link>
        </Button>
        <Button size="sm" variant="outline" asChild>
          <Link href="/panel/agenda">Volver al panel</Link>
        </Button>
      </div>
    </div>
  );
}

function CustomerSection({
  slug,
  demo,
  form,
  summary,
  request,
  onBooked,
  onSlotTaken,
}: {
  slug: string;
  demo: boolean;
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
    setValue,
    formState: { errors },
  } = form;

  const fillDemoData = () => {
    setValue("fullName", DEMO_CUSTOMER.fullName, { shouldValidate: true });
    setValue("email", DEMO_CUSTOMER.email, { shouldValidate: true });
  };

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
      {demo && (
        <Button type="button" variant="outline" size="sm" className="justify-self-start" onClick={fillDemoData}>
          Rellenar con datos de prueba
        </Button>
      )}
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

function BookingConfirmation({
  slug,
  booking,
  demo,
  onAgain,
}: {
  slug: string;
  booking: Booking;
  demo: boolean;
  onAgain: () => void;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-2xl">
          <CalendarCheck className="size-6 text-green-600" aria-hidden />
          ¡Reserva confirmada!
        </CardTitle>
        <CardDescription>
          {demo ? (
            <>
              En la demo no se envía: el email para <strong>{booking.customerEmail}</strong> (y el aviso al negocio)
              están en la bandeja de la demo.
            </>
          ) : (
            <>
              Te hemos enviado un email a <strong>{booking.customerEmail}</strong> con los datos y un enlace para
              cambiarla o cancelarla. Si no lo ves, mira en la carpeta de spam.
            </>
          )}
        </CardDescription>
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
        </dl>
        <div className="flex flex-wrap gap-2">
          <Button asChild>
            <Link href={manageHref(slug, booking.manageToken)}>Ver o cambiar mi cita</Link>
          </Button>
          {demo && (
            <Button variant="outline" asChild>
              <Link href="/panel/bandeja">Ver el email en la bandeja</Link>
            </Button>
          )}
          <Button variant="outline" onClick={onAgain}>
            Hacer otra reserva
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
