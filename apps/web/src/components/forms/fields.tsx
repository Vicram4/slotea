import { useId, type ComponentProps, type ReactNode } from "react";

import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

type FieldChrome = { label: string; error?: string; hint?: ReactNode };

/** Etiqueta, pista y error con `aria-describedby`: lo mismo que `FormField`, para cualquier control. */
function Field({
  label,
  error,
  hint,
  children,
}: FieldChrome & { children: (id: string, describedBy: string | undefined) => ReactNode }) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(" ") || undefined;
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      {children(id, describedBy)}
      {hint && (
        <p id={hintId} className="text-muted-foreground text-sm">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-destructive text-sm">
          {error}
        </p>
      )}
    </div>
  );
}

export function TextareaField({ label, error, hint, ...props }: FieldChrome & ComponentProps<typeof Textarea>) {
  return (
    <Field label={label} error={error} hint={hint}>
      {(id, describedBy) => (
        <Textarea id={id} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...props} />
      )}
    </Field>
  );
}

/** `<select>` nativo: accesible, funciona con el teclado del móvil y sin dependencias. */
export function SelectField({ label, error, hint, className, ...props }: FieldChrome & ComponentProps<"select">) {
  return (
    <Field label={label} error={error} hint={hint}>
      {(id, describedBy) => (
        <select
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            "border-input focus-visible:border-ring focus-visible:ring-ring/50 h-9 w-full rounded-md border bg-transparent px-3 text-sm shadow-xs outline-none focus-visible:ring-[3px] aria-invalid:border-destructive",
            className,
          )}
          {...props}
        />
      )}
    </Field>
  );
}
