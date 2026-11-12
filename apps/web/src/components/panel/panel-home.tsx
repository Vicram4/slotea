"use client";

import { Check, CheckCircle2, Circle, Copy } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useCanEdit, useSaveStaff, useServices, useStaff } from "@/lib/catalog";
import { roleLabels, useMe } from "@/lib/session";

/** Inicio del panel: quién soy y qué falta para que el negocio pueda recibir reservas. */
export function PanelHome() {
  const me = useMe().data;
  if (!me?.activeBusiness) return null;
  const { user, activeBusiness } = me;

  return (
    <>
      <Card>
        <CardHeader>
          <CardTitle className="text-2xl">Hola, {user.fullName}</CardTitle>
          <CardDescription>{user.email}</CardDescription>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Negocio</dt>
            <dd className="font-medium">{activeBusiness.name}</dd>
            <dt className="text-muted-foreground">Tu página</dt>
            <dd>
              <BookingPageLink slug={activeBusiness.slug} />
            </dd>
            <dt className="text-muted-foreground">Tu rol</dt>
            <dd className="font-medium">{roleLabels[activeBusiness.role]}</dd>
          </dl>
        </CardContent>
      </Card>
      <SetupChecklist userId={user.id} fullName={user.fullName} />
    </>
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
    <span className="flex flex-wrap items-center gap-2">
      <a href={`/${slug}`} target="_blank" rel="noreferrer" className="font-medium underline-offset-4 hover:underline">
        {url.replace(/^https?:\/\//, "")}
      </a>
      <Button variant="ghost" size="icon" className="size-7" aria-label="Copiar enlace" onClick={copy}>
        {copied ? <Check aria-hidden /> : <Copy aria-hidden />}
      </Button>
      {copied && (
        <span role="status" className="text-muted-foreground text-xs">
          Copiado
        </span>
      )}
    </span>
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

  const onAddMe = async () => {
    const created = await addMe.mutateAsync({ body: { displayName: fullName, userId } }).catch(() => null);
    if (created) router.push(`/panel/profesionales/${created.id}`);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Configura tu negocio</CardTitle>
        <CardDescription>Cuando esté todo listo, tus clientes podrán reservar en tu página.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <ol className="grid gap-3">
          {steps.map((step) => (
            <li key={step.label} className="flex items-center gap-3">
              {step.done ? (
                <CheckCircle2 className="size-5 text-green-600" aria-hidden />
              ) : (
                <Circle className="text-muted-foreground size-5" aria-hidden />
              )}
              <Link href={step.href} className="underline-offset-4 hover:underline">
                {step.label}
              </Link>
              <span className="sr-only">{step.done ? "(hecho)" : "(pendiente)"}</span>
            </li>
          ))}
        </ol>
        {canEdit && !amStaff && (
          <div className="flex flex-wrap items-center gap-3 border-t pt-4 text-sm">
            <span>¿También atiendes tú a los clientes?</span>
            <Button size="sm" onClick={onAddMe} disabled={addMe.isPending}>
              {addMe.isPending ? "Añadiendo…" : "Añádete como profesional"}
            </Button>
            {addMe.isError && <span className="text-destructive">No se pudo añadir. Inténtalo de nuevo.</span>}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
