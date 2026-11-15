"use client";

import { ArrowRight, Check, CheckCircle2, Circle, Copy } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useAgenda, useBusiness } from "@/lib/agenda";
import { useCanEdit, useSaveStaff, useServices, useStaff } from "@/lib/catalog";
import { formatLongDate, todayIn, type IsoDate } from "@/lib/dates";
import { roleLabels, useMe } from "@/lib/session";
import { cn } from "@/lib/utils";

import { PageHeader } from "./page-header";

/** Inicio del panel: las citas de hoy, el enlace para compartir y lo que falte por configurar. */
export function PanelHome() {
  const me = useMe().data;
  const business = useBusiness();
  if (!me?.activeBusiness) return null;
  const { user, activeBusiness } = me;
  const today = business.data ? todayIn(business.data.timezone) : undefined;

  return (
    <>
      <PageHeader
        title={`Hola, ${user.fullName}`}
        description={
          <>
            {today && <span className="first-letter:uppercase">{formatLongDate(today)} · </span>}
            {roleLabels[activeBusiness.role]} en {activeBusiness.name}
          </>
        }
      />
      <div className="grid items-start gap-8 lg:grid-cols-[1fr_20rem]">
        <TodaySection today={today} businessError={business.isError} />
        <div className="grid min-w-0 gap-6">
          <section aria-labelledby="booking-page-title" className="grid min-w-0 gap-3 rounded-lg border p-4">
            <div className="grid gap-1">
              <h2 id="booking-page-title" className="text-sm font-medium">
                Tu página de reservas
              </h2>
              <p className="text-muted-foreground text-xs">
                Compártela con tus clientes: en tu perfil de Instagram, en Google o por WhatsApp.
              </p>
            </div>
            <BookingPageLink slug={activeBusiness.slug} />
          </section>
          <SetupChecklist userId={user.id} fullName={user.fullName} />
        </div>
      </div>
    </>
  );
}

/** Las citas de hoy de un vistazo (para el empleado, solo las suyas: lo decide la API). */
function TodaySection({ today, businessError }: { today?: IsoDate; businessError: boolean }) {
  const agenda = useAgenda({ from: today ?? "", to: today ?? "" }, !!today);
  const active = agenda.data?.appointments.filter((a) => a.status !== "CANCELLED") ?? [];
  const showStaff = (agenda.data?.staff.length ?? 0) > 1;

  return (
    <section aria-labelledby="today-title" className="grid gap-3">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id="today-title" className="text-sm font-medium">
          Hoy
          <span className="text-muted-foreground font-normal">
            {" · "}
            {agenda.isSuccess
              ? active.length === 0
                ? "sin citas"
                : `${active.length} ${active.length === 1 ? "cita" : "citas"}`
              : agenda.isError || businessError
                ? "no se pudieron cargar las citas"
                : "cargando…"}
          </span>
        </h2>
        <Link
          href="/panel/agenda"
          className="text-muted-foreground hover:text-foreground flex items-center gap-1 text-sm"
        >
          Abrir la agenda <ArrowRight className="size-3.5" aria-hidden />
        </Link>
      </div>
      {agenda.isSuccess && active.length === 0 && (
        <p className="text-muted-foreground rounded-lg border border-dashed p-8 text-center text-sm">
          No hay citas para hoy.
        </p>
      )}
      {active.length > 0 && (
        <ul aria-label="Citas de hoy" className="divide-y rounded-lg border">
          {active.map((a) => (
            <li key={a.id} className="flex items-center gap-4 px-4 py-3 text-sm">
              <span className="w-12 font-medium tabular-nums">{a.startTime}</span>
              <span className="grid min-w-0 flex-1">
                <span className="truncate font-medium">{a.customer.fullName}</span>
                <span className="text-muted-foreground truncate">{a.serviceName}</span>
              </span>
              {showStaff && <span className="text-muted-foreground text-xs">{a.staffName}</span>}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Enlace a la página pública de reservas y botón para copiarlo (para compartirlo con los clientes). */
function BookingPageLink({ slug }: { slug: string }) {
  const [copied, setCopied] = useState(false);
  const url = `${globalThis.location?.origin ?? ""}/${slug}`;
  const copy = async () => {
    await navigator.clipboard?.writeText(url).catch(() => undefined);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <div className="flex min-w-0 items-center gap-1 rounded-md border py-1 pr-1 pl-3">
      <a
        href={`/${slug}`}
        target="_blank"
        rel="noreferrer"
        className="min-w-0 flex-1 truncate text-sm font-medium underline-offset-4 hover:underline"
      >
        {url.replace(/^https?:\/\//, "")}
      </a>
      <Button variant="ghost" size="sm" aria-label="Copiar enlace" onClick={copy}>
        {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
        <span aria-hidden>{copied ? "Copiado" : "Copiar"}</span>
      </Button>
      {copied && (
        <span role="status" className="sr-only">
          Copiado
        </span>
      )}
    </div>
  );
}

function SetupChecklist({ userId, fullName }: { userId: string; fullName: string }) {
  const router = useRouter();
  const canEdit = useCanEdit();
  const services = useServices();
  const staff = useStaff();
  const addMe = useSaveStaff();

  if (services.isPending || staff.isPending) return <p role="status">Cargando…</p>;
  if (services.isError || staff.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudo cargar el catálogo. Recarga la página.</AlertDescription>
      </Alert>
    );
  }

  const activeStaff = staff.data.filter((s) => s.active);
  const steps = [
    {
      done: services.data.some((s) => s.active),
      label: "Crea los servicios que ofreces",
      href: "/panel/servicios",
    },
    { done: activeStaff.length > 0, label: "Añade a tu equipo", href: "/panel/profesionales" },
    {
      done: activeStaff.length > 0 && activeStaff.every((s) => s.serviceIds.length > 0),
      label: "Indica qué servicios hace cada profesional y su horario",
      href: "/panel/profesionales",
    },
  ];
  const amStaff = staff.data.some((s) => s.userId === userId);
  const pending = steps.filter((step) => !step.done).length;
  const offerAddMe = canEdit && !amStaff;
  // Con todo listo, la lista sobra: solo queda la invitación a añadirse (si procede).
  if (pending === 0 && !offerAddMe) return null;

  const onAddMe = async () => {
    const created = await addMe.mutateAsync({ body: { displayName: fullName, userId } }).catch(() => null);
    if (created) router.push(`/panel/profesionales/${created.id}`);
  };

  return (
    <section aria-labelledby="setup-title" className="grid gap-3 rounded-lg border p-4">
      {pending > 0 && (
        <>
          <div className="grid gap-1">
            <h2 id="setup-title" className="text-sm font-medium">
              Configura tu negocio
            </h2>
            <p className="text-muted-foreground text-xs">
              {pending === 1 ? "Te queda un paso" : `Te quedan ${pending} pasos`} para que tus clientes puedan
              reservar.
            </p>
          </div>
          <ol className="grid gap-2 text-sm">
            {steps.map((step) => (
              <li key={step.label} className="flex items-start gap-2.5">
                {step.done ? (
                  <CheckCircle2 className="text-primary mt-0.5 size-4 shrink-0" aria-hidden />
                ) : (
                  <Circle className="text-muted-foreground mt-0.5 size-4 shrink-0" aria-hidden />
                )}
                <Link
                  href={step.href}
                  className={cn("underline-offset-4 hover:underline", step.done && "text-muted-foreground line-through")}
                >
                  {step.label}
                </Link>
                <span className="sr-only">{step.done ? "(hecho)" : "(pendiente)"}</span>
              </li>
            ))}
          </ol>
        </>
      )}
      {offerAddMe && (
        <div className={cn("grid gap-2 text-sm", pending > 0 && "border-t pt-3")}>
          {pending === 0 && (
            <h2 id="setup-title" className="text-sm font-medium">
              ¿También atiendes tú?
            </h2>
          )}
          <p className="text-muted-foreground text-xs">Añádete como profesional y tus clientes podrán reservar contigo.</p>
          <Button
            size="sm"
            variant="outline"
            onClick={onAddMe}
            disabled={addMe.isPending}
            className="justify-self-start"
          >
            {addMe.isPending ? "Añadiendo…" : "Añádete como profesional"}
          </Button>
          {addMe.isError && <span className="text-destructive">No se pudo añadir. Inténtalo de nuevo.</span>}
        </div>
      )}
    </section>
  );
}
