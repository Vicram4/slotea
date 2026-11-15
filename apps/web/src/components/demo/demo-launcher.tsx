"use client";

import { CalendarDays, Store, UserRound } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState, type ComponentType } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";
import { type DemoEntry, demoLanding, useStartDemo } from "@/lib/demo";

/** A partir de aquí, lo más probable es que la API esté arrancando (escala a cero). */
export const WAKE_UP_AFTER_MS = 2500;

const ENTRIES: { entry: DemoEntry; label: string; description: string; icon: ComponentType<{ className?: string }> }[] = [
  {
    entry: "OWNER",
    label: "Entrar como negocio",
    description: "La agenda del dueño, el catálogo, los horarios y la bandeja de emails.",
    icon: Store,
  },
  {
    entry: "STAFF",
    label: "Entrar como empleado",
    description: "Solo su agenda: marca cómo fueron sus citas y gestiona sus ausencias.",
    icon: CalendarDays,
  },
  {
    entry: "CUSTOMER",
    label: "Reservar como cliente",
    description: "La página pública de reservas, sin cuenta.",
    icon: UserRound,
  },
];

/**
 * «Prueba la demo» de la portada. Cada clic crea una copia de la barbería de demostración para este
 * visitante (sus cambios solo los ve quien la ha creado y se borran en 24 h) y abre sesión en ella.
 */
export function DemoLauncher({ wakeUpAfterMs = WAKE_UP_AFTER_MS }: { wakeUpAfterMs?: number }) {
  const router = useRouter();
  const start = useStartDemo();
  const [chosen, setChosen] = useState<DemoEntry | null>(null);
  const [slow, setSlow] = useState(false);

  useEffect(() => {
    if (!start.isPending) return;
    const timer = setTimeout(() => setSlow(true), wakeUpAfterMs);
    return () => {
      clearTimeout(timer);
      setSlow(false);
    };
  }, [start.isPending, wakeUpAfterMs]);

  const launch = (entry: DemoEntry) => {
    setChosen(entry);
    start.mutate(entry, { onSuccess: (me) => router.push(demoLanding(entry, me)) });
  };

  const busy = start.isPending || start.isSuccess;

  return (
    <section aria-labelledby="demo-title" className="grid gap-4 rounded-xl border p-6">
      <div className="grid gap-1">
        <h2 id="demo-title" className="text-xl font-semibold">
          Prueba la demo
        </h2>
        <p className="text-muted-foreground text-sm">
          Una barbería ficticia con su agenda llena. Sin registrarte: tus cambios solo los ves tú y se borran en 24
          horas. No se envía ningún email; los verás en la bandeja de la demo.
        </p>
      </div>
      <ul className="grid gap-3 sm:grid-cols-3">
        {ENTRIES.map(({ entry, label, description, icon: Icon }) => (
          <li key={entry} className="grid">
            <Button
              variant={entry === "OWNER" ? "default" : "outline"}
              className="h-auto flex-col items-start gap-1 p-4 text-left whitespace-normal"
              disabled={busy}
              onClick={() => launch(entry)}
            >
              <span className="flex items-center gap-2 font-semibold">
                <Icon className="size-4" aria-hidden />
                {busy && chosen === entry ? "Preparando…" : label}
              </span>
              <span className="text-xs font-normal opacity-80">{description}</span>
            </Button>
          </li>
        ))}
      </ul>
      {start.isPending && (
        <p role="status" className="text-muted-foreground text-sm">
          {slow
            ? "Despertando el servidor… La demo gratuita se apaga cuando nadie la usa; tarda unos segundos en arrancar."
            : "Preparando tu copia de la demo…"}
        </p>
      )}
      {start.isError && (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(start.error)}</AlertDescription>
        </Alert>
      )}
    </section>
  );
}

function errorMessage(error: Error): string {
  // 503: la demo está llena; 429: demasiadas copias seguidas. La API explica cuál.
  if (error instanceof ApiError && [429, 503].includes(error.status) && error.problem.detail) {
    return error.problem.detail;
  }
  return "No se pudo abrir la demo. Inténtalo de nuevo en unos segundos.";
}
