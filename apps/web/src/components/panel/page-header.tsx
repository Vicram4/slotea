import type { ReactNode } from "react";

/**
 * Cabecera común de las páginas del panel, igual que la de la agenda: título, una línea que resume lo
 * que hay (con datos, no con un eslogan) y las acciones a la derecha.
 */
export function PageHeader({
  title,
  description,
  actions,
  before,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  /** Encima del título, p. ej. el enlace para volver a la lista. */
  before?: ReactNode;
}) {
  return (
    <header className="grid gap-3">
      {before}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="grid gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {description && <p className="text-muted-foreground max-w-2xl text-sm text-pretty">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>
  );
}
