"use client";

import { ArrowLeft, CalendarDays, Store, UserRound } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ComponentType } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api/client";
import { type DemoEntry, type DemoSector, demoLanding, useStartDemo } from "@/lib/demo";

import { DEMO_SECTOR_ORDER, DEMO_SECTORS } from "./demo-sectors";

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
 * «Prueba la demo» de la portada, en dos pasos: el sector y después cómo entrar. Cada clic crea una
 * copia del negocio de ese sector para este visitante (sus cambios solo los ve quien la ha creado y se
 * borran en 24 h) y abre sesión en ella.
 */
export function DemoLauncher({ wakeUpAfterMs = WAKE_UP_AFTER_MS }: { wakeUpAfterMs?: number }) {
  const router = useRouter();
  const start = useStartDemo();
  const [sector, setSector] = useState<DemoSector | null>(null);
  const [chosen, setChosen] = useState<DemoEntry | null>(null);
  const [slow, setSlow] = useState(false);
  // Al cambiar de paso, el foco va al contenido nuevo (y al volver, al sector que se había elegido).
  const stepHeading = useRef<HTMLHeadingElement>(null);
  const sectorButtons = useRef(new Map<DemoSector, HTMLButtonElement>());
  const returnTo = useRef<DemoSector | null>(null);

  useEffect(() => {
    if (!start.isPending) return;
    const timer = setTimeout(() => setSlow(true), wakeUpAfterMs);
    return () => {
      clearTimeout(timer);
      setSlow(false);
    };
  }, [start.isPending, wakeUpAfterMs]);

  useEffect(() => {
    if (sector) {
      stepHeading.current?.focus();
    } else if (returnTo.current) {
      sectorButtons.current.get(returnTo.current)?.focus();
    }
  }, [sector]);

  const launch = (entry: DemoEntry) => {
    if (!sector) return;
    setChosen(entry);
    start.mutate({ sector, entry }, { onSuccess: (me) => router.push(demoLanding(entry, me)) });
  };

  const back = () => {
    returnTo.current = sector;
    start.reset();
    setSector(null);
  };

  const busy = start.isPending || start.isSuccess;
  const info = sector ? DEMO_SECTORS[sector] : null;
  const entries = ENTRIES.filter(({ entry }) => entry !== "STAFF" || info?.hasStaff);

  return (
    <section aria-labelledby="demo-title" className="grid gap-4 rounded-xl border p-6">
      <div className="grid gap-1">
        <h2 id="demo-title" className="text-xl font-semibold">
          Prueba la demo
        </h2>
        <p className="text-muted-foreground text-sm">
          Un negocio ficticio con su agenda llena. Sin registrarte: tus cambios solo los ves tú y se borran en 24 horas.
          No se envía ningún email; los verás en la bandeja de la demo.
        </p>
      </div>

      {!sector || !info ? (
        <div className="grid gap-3">
          <h3 className="text-sm font-medium">Elige un sector</h3>
          <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {DEMO_SECTOR_ORDER.map((key) => {
              const { name, tagline, icon: Icon } = DEMO_SECTORS[key];
              return (
                <li key={key} className="grid">
                  <Button
                    ref={(node) => {
                      if (node) sectorButtons.current.set(key, node);
                      else sectorButtons.current.delete(key);
                    }}
                    variant="outline"
                    className="h-auto flex-col items-start gap-1 p-4 text-left whitespace-normal"
                    onClick={() => setSector(key)}
                  >
                    <span className="flex items-center gap-2 font-semibold">
                      <Icon className="size-4" aria-hidden />
                      {name}
                    </span>
                    <span className="text-xs font-normal opacity-80">{tagline}</span>
                  </Button>
                </li>
              );
            })}
          </ul>
        </div>
      ) : (
        <div className="grid gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 ref={stepHeading} tabIndex={-1} className="flex items-center gap-2 text-sm font-medium outline-none">
              <info.icon className="size-4" aria-hidden />
              {info.name}: ¿cómo quieres entrar?
            </h3>
            <Button variant="ghost" size="sm" onClick={back} disabled={busy}>
              <ArrowLeft className="size-4" aria-hidden />
              Otro sector
            </Button>
          </div>
          {!info.hasStaff && (
            <p className="text-muted-foreground text-sm">
              Es una consulta de una sola profesional: no tiene vista de empleado.
            </p>
          )}
          <ul className={entries.length === 3 ? "grid gap-3 sm:grid-cols-3" : "grid gap-3 sm:grid-cols-2"}>
            {entries.map(({ entry, label, description, icon: Icon }) => (
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
        </div>
      )}

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
