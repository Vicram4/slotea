"use client";

import { FlaskConical } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import type { Me } from "@/lib/api/client";
import { useSwitchDemoRole } from "@/lib/demo";

/**
 * Aviso y atajos de la demo: cambiar de dueño a empleado (misma copia), reservar como cliente y abrir
 * la bandeja de emails. Solo se muestra en una copia de la demo. El cambio de rol solo aparece si la
 * copia tiene cuenta del otro rol (`demoCanSwitch`): en psicología no hay empleado.
 */
export function DemoBar({ me }: { me: Me }) {
  const router = useRouter();
  const switchRole = useSwitchDemoRole();
  const business = me.activeBusiness;
  if (!business?.demo) return null;

  const owner = business.role === "OWNER";
  const onSwitch = () =>
    switchRole.mutate(owner ? "STAFF" : "OWNER", { onSuccess: () => router.push("/panel/agenda") });

  return (
    <div
      role="region"
      aria-label="Demo"
      className="bg-foreground text-background flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-1.5 text-sm max-md:text-xs md:col-span-2 md:gap-x-4 md:py-2 [&_[data-slot=button]]:border-white/20 [&_[data-slot=button]]:bg-transparent [&_[data-slot=button]]:text-inherit [&_[data-slot=button]:hover]:bg-white/10"
    >
      <p className="flex shrink-0 items-center gap-2">
        <FlaskConical className="size-4 shrink-0" aria-hidden />
        <span>
          <strong>Demo.</strong>
          {/* En el móvil, el aviso corto: la barra no puede comerse la pantalla. */}
          <span className="hidden md:inline"> Tus cambios solo los ves tú y se borran en 24 horas. No se envía ningún email.</span>
          <span className="md:hidden"> Se borra en 24 h.</span>
        </span>
      </p>
      <div className="-mr-4 flex min-w-0 flex-1 gap-2 overflow-x-auto pr-4 max-md:[&_[data-slot=button]]:h-7 max-md:[&_[data-slot=button]]:px-2.5 max-md:[&_[data-slot=button]]:text-xs md:mr-0 md:flex-none md:flex-wrap md:pr-0">
        {business.demoCanSwitch && (
          <Button size="sm" variant="outline" onClick={onSwitch} disabled={switchRole.isPending}>
            {switchRole.isPending ? "Cambiando…" : owner ? "Ver como empleado" : "Ver como dueño"}
          </Button>
        )}
        <Button size="sm" variant="outline" asChild>
          <Link href={`/${business.slug}`}>Reservar como cliente</Link>
        </Button>
        {owner && (
          <Button size="sm" variant="outline" asChild>
            <Link href="/panel/bandeja">Bandeja de la demo</Link>
          </Button>
        )}
      </div>
      {switchRole.isError && (
        <p role="alert" className="w-full text-red-300">
          No se pudo cambiar de usuario. Si la demo ha caducado, vuelve a la portada y crea otra.
        </p>
      )}
    </div>
  );
}
