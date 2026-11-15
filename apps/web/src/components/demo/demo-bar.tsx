"use client";

import { FlaskConical } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { Button } from "@/components/ui/button";
import type { Me } from "@/lib/api/client";
import { useSwitchDemoRole } from "@/lib/demo";

/**
 * Aviso y atajos de la demo: cambiar de dueño a empleado (misma copia), reservar como cliente y abrir
 * la bandeja de emails. Solo se muestra en una copia de la demo.
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
      className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-950 md:col-span-2"
    >
      <p className="flex items-center gap-2">
        <FlaskConical className="size-4 shrink-0" aria-hidden />
        <span>
          <strong>Demo.</strong> Tus cambios solo los ves tú y se borran en 24 horas. No se envía ningún email.
        </span>
      </p>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="outline" onClick={onSwitch} disabled={switchRole.isPending}>
          {switchRole.isPending ? "Cambiando…" : owner ? "Ver como empleado" : "Ver como dueño"}
        </Button>
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
        <p role="alert" className="text-destructive w-full">
          No se pudo cambiar de usuario. Si la demo ha caducado, vuelve a la portada y crea otra.
        </p>
      )}
    </div>
  );
}
