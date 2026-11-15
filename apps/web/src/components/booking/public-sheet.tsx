import type { LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import type { PublicBusiness, PublicStaff } from "@/lib/booking";
import { cn } from "@/lib/utils";

/**
 * La ficha de las páginas públicas (reservar y gestionar la cita): el negocio y un resumen a la
 * izquierda, el contenido a la derecha. En el móvil, una sola columna.
 */
export function PublicSheet({
  business,
  tagline,
  aside,
  children,
}: {
  business: PublicBusiness;
  tagline: string;
  aside?: ReactNode;
  children: ReactNode;
}) {
  return (
    <>
      {business.demo && <DemoNotice />}
      <div className="bg-background grid overflow-hidden rounded-xl border shadow-xs md:grid-cols-[17rem_1fr]">
        <aside className="border-b p-6 md:border-r md:border-b-0">
          {/* En escritorio, el resumen acompaña al bajar. */}
          <div className="grid gap-6 md:sticky md:top-8">
            <header className="grid gap-4">
              <span
                aria-hidden
                className="bg-primary text-primary-foreground grid size-10 place-items-center rounded-lg text-lg font-semibold"
              >
                {business.name.trim().charAt(0).toUpperCase()}
              </span>
              <div className="grid gap-1">
                <h1 className="text-xl font-semibold tracking-tight">{business.name}</h1>
                <p className="text-muted-foreground text-sm">{tagline}</p>
              </div>
            </header>
            {aside}
          </div>
        </aside>
        <div className="grid content-start gap-10 p-6 md:p-8">{children}</div>
      </div>
    </>
  );
}

/** En una copia de la demo: aquí se juega el papel del cliente, y los emails van a la bandeja. */
function DemoNotice() {
  return (
    <div
      role="note"
      className="bg-background flex flex-wrap items-center justify-between gap-3 rounded-lg border border-dashed px-4 py-3 text-sm"
    >
      <p>
        <strong className="font-medium">Demo:</strong> estás en el papel del cliente. No sale ningún email: lo verás en
        la bandeja de la demo.
      </p>
      <div className="flex gap-2">
        <Button size="sm" variant="outline" asChild>
          <Link href="/panel/bandeja">Bandeja de la demo</Link>
        </Button>
        <Button size="sm" variant="ghost" asChild>
          <Link href="/panel/agenda">Volver al panel</Link>
        </Button>
      </div>
    </div>
  );
}

export function StepHeading({ id, hint, children }: { id: string; hint?: string; children: ReactNode }) {
  return (
    <div className="grid gap-1">
      <h2 id={id} className="font-semibold">
        {children}
      </h2>
      {hint && <p className="text-muted-foreground text-sm">{hint}</p>}
    </div>
  );
}

/** Lista de datos con icono para la columna de la izquierda. El primer hijo suele ser el título. */
export function SummaryList({ label, children }: { label: string; children: ReactNode }) {
  return (
    <dl aria-label={label} className="grid gap-2.5 border-t pt-6 text-sm">
      {children}
    </dl>
  );
}

export function SummaryItem({
  icon: Icon,
  label,
  className,
  children,
}: {
  icon: LucideIcon;
  label: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className="text-muted-foreground flex items-start gap-2">
      <Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
      <dt className="sr-only">{label}</dt>
      <dd className={cn("first-letter:uppercase", className)}>{children}</dd>
    </div>
  );
}

/** Profesionales como botones de radio, con «Me da igual» delante (valor ""). */
export function StaffChoice({
  labelledBy,
  staff,
  value,
  onChange,
}: {
  labelledBy: string;
  staff: Pick<PublicStaff, "id" | "displayName">[];
  value: string;
  onChange: (staffId: string) => void;
}) {
  return (
    <div role="radiogroup" aria-labelledby={labelledBy} className="flex flex-wrap gap-2">
      {[{ id: "", displayName: "Me da igual" }, ...staff].map((s) => (
        <label
          key={s.id}
          className={cn(
            "cursor-pointer rounded-full border px-4 py-1.5 text-sm",
            "has-[:focus-visible]:ring-ring/50 has-[:focus-visible]:ring-[3px]",
            s.id === value ? "bg-primary text-primary-foreground border-primary" : "hover:bg-muted/60",
          )}
        >
          <input
            type="radio"
            name={`${labelledBy}-staff`}
            value={s.id}
            checked={s.id === value}
            onChange={() => onChange(s.id)}
            className="sr-only"
          />
          {s.displayName}
        </label>
      ))}
    </div>
  );
}
