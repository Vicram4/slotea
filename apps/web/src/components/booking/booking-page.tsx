"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { CalendarDays, Check, Clock, User, Wallet } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";
import { useForm, type UseFormReturn } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { TextareaField } from "@/components/forms/fields";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";
import {
  type Booking,
  manageHref,
  type PublicBusiness,
  type PublicService,
  type Slot,
  useBook,
  usePublicBusiness,
  useWeekAvailability,
} from "@/lib/booking";
import { useBrandColor } from "@/lib/brand";
import { DEMO_CUSTOMER } from "@/lib/demo";
import { addDays, formatLongDate, isOtherTimeZone, type IsoDate, shortDay, todayIn } from "@/lib/dates";
import { cn } from "@/lib/utils";
import { customerSchema, type CustomerData, type CustomerValues } from "@/lib/validation/booking";
import { formatDuration, formatPrice } from "@/lib/validation/catalog";

import { PublicSheet, StaffChoice, StepHeading, SummaryItem, SummaryList } from "./public-sheet";
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
            ? "No encontramos este negocio. ¿Seguro que la dirección está bien escrita?"
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
const TAGLINE = "Elige servicio, día y hora. Sin llamadas ni esperas.";

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
  // Si solo una persona hace el servicio, no hay nada que elegir: se dice quién es y ya.
  const staffName =
    staffForService.length === 1
      ? staffForService[0]!.displayName
      : (staffForService.find((s) => s.id === staffId)?.displayName ?? "Quien esté libre");

  const resetTime = () => {
    setSlot(null);
    setConflict(null);
  };

  if (business.services.length === 0) {
    return (
      <PublicSheet business={business} tagline={TAGLINE}>
        <p className="text-muted-foreground">Este negocio todavía no tiene la agenda abierta online.</p>
      </PublicSheet>
    );
  }

  return (
    <PublicSheet
      business={business}
      tagline={TAGLINE}
      aside={
        service && (
          <BookingSummary
            service={service}
            staffName={staffName}
            when={slot && date ? `${formatLongDate(date)}, ${slot.time}` : null}
          />
        )
      }
    >
      <section aria-labelledby="step-service" className="grid gap-4">
        <StepHeading id="step-service">¿Qué necesitas?</StepHeading>
        <div role="radiogroup" aria-labelledby="step-service" className="divide-y overflow-hidden rounded-lg border">
          {business.services.map((s) => {
            const checked = s.id === serviceId;
            return (
              <label
                key={s.id}
                className={cn(
                  "relative flex cursor-pointer items-start justify-between gap-4 px-4 py-3.5",
                  "has-[:focus-visible]:ring-ring/50 has-[:focus-visible]:ring-[3px] has-[:focus-visible]:ring-inset",
                  checked ? "bg-primary/5" : "hover:bg-muted/60",
                )}
              >
                {checked && <span aria-hidden className="bg-primary absolute inset-y-0 left-0 w-1" />}
                <input
                  type="radio"
                  name="service"
                  value={s.id}
                  checked={checked}
                  onChange={() => {
                    setServiceId(s.id);
                    setStaffId("");
                    setDate(null);
                    resetTime();
                  }}
                  className="sr-only"
                />
                <span className="grid gap-0.5">
                  <span className="font-medium">{s.name}</span>
                  {s.description && <span className={cn("text-sm", secondary(checked))}>{s.description}</span>}
                </span>
                <span className={cn("text-sm whitespace-nowrap tabular-nums", secondary(checked))}>
                  {formatDuration(s.durationMin)}
                  {s.priceCents != null && ` · ${formatPrice(s.priceCents)}`}
                </span>
              </label>
            );
          })}
        </div>
      </section>

      {service && staffForService.length > 1 && (
        <section aria-labelledby="step-staff" className="grid gap-4">
          <StepHeading id="step-staff" hint="Si te da igual, te enseñamos los huecos de todo el equipo.">
            ¿Con quién?
          </StepHeading>
          <StaffChoice
            labelledBy="step-staff"
            staff={staffForService}
            value={staffId}
            onChange={(id) => {
              setStaffId(id);
              resetTime();
            }}
          />
        </section>
      )}

      {service && (
        <section ref={timeSection} aria-labelledby="step-time" className="grid gap-4 outline-none" tabIndex={-1}>
          <StepHeading
            id="step-time"
            hint={`Solo te enseñamos horas con ${formatDuration(service.durationMin)} libres por delante.`}
          >
            ¿Cuándo te viene bien?
          </StepHeading>
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
          submitLabel={`Confirmar: ${shortDay(date).weekday} ${shortDay(date).dayMonth}, ${slot.time}`}
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
    </PublicSheet>
  );
}

/** Lo elegido hasta ahora, siempre a la vista. */
function BookingSummary({
  service,
  staffName,
  when,
}: {
  service: PublicService;
  staffName: string;
  when: string | null;
}) {
  return (
    <SummaryList label="Tu reserva">
      <div>
        <dt className="sr-only">Servicio</dt>
        <dd className="text-base font-medium">{service.name}</dd>
      </div>
      <SummaryItem icon={Clock} label="Duración">
        {formatDuration(service.durationMin)}
      </SummaryItem>
      {service.priceCents != null && (
        <SummaryItem icon={Wallet} label="Precio">
          {formatPrice(service.priceCents)}
        </SummaryItem>
      )}
      <SummaryItem icon={User} label="Profesional">
        {staffName}
      </SummaryItem>
      {when && (
        <SummaryItem icon={CalendarDays} label="Cuándo">
          {when}
        </SummaryItem>
      )}
    </SummaryList>
  );
}

function CustomerSection({
  slug,
  demo,
  form,
  submitLabel,
  request,
  onBooked,
  onSlotTaken,
}: {
  slug: string;
  demo: boolean;
  form: UseFormReturn<CustomerValues, unknown, CustomerData>;
  submitLabel: string;
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
    <section aria-labelledby="step-customer" className="grid gap-4">
      <StepHeading id="step-customer">¿A quién esperamos?</StepHeading>
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
          hint="Ahí te llega la confirmación, con un enlace para cambiar o cancelar la cita."
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
        <Button type="submit" size="lg" disabled={book.isPending}>
          {book.isPending ? "Reservando…" : submitLabel}
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
    <div className="bg-background mx-auto grid w-full max-w-lg gap-6 rounded-xl border p-6 shadow-xs sm:p-8">
      <span className="bg-primary text-primary-foreground grid size-10 place-items-center rounded-full">
        <Check className="size-5" aria-hidden />
      </span>
      <div className="grid gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Hecho. Te esperamos.</h1>
        <p className="text-muted-foreground text-sm">
          {demo ? (
            <>
              En la demo no sale ningún email: el de <strong className="text-foreground">{booking.customerEmail}</strong>{" "}
              (y el aviso al negocio) están en la bandeja de la demo.
            </>
          ) : (
            <>
              Te hemos mandado los detalles a <strong className="text-foreground">{booking.customerEmail}</strong>, con
              un enlace para cambiar o cancelar la cita. Si no lo ves, mira en spam.
            </>
          )}
        </p>
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-3 border-y py-5 text-sm">
        <dt className="text-muted-foreground">Qué</dt>
        <dd className="font-medium">{booking.serviceName}</dd>
        <dt className="text-muted-foreground">Cuándo</dt>
        <dd className="font-medium first-letter:uppercase">
          {formatLongDate(booking.date)}, {booking.time}
          {isOtherTimeZone(booking.timezone) && ` (${booking.timezone})`}
        </dd>
        <dt className="text-muted-foreground">Con quién</dt>
        <dd className="font-medium">{booking.staffName}</dd>
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
        <Button variant="ghost" onClick={onAgain}>
          Hacer otra reserva
        </Button>
      </div>
    </div>
  );
}

/**
 * Texto secundario de una fila: el gris de siempre, o algo más oscuro sobre el tinte de la fila elegida
 * (el gris sobre el tinte del color de marca no llega a 4,5:1).
 */
function secondary(checked: boolean): string {
  return checked ? "text-foreground/75" : "text-muted-foreground";
}
