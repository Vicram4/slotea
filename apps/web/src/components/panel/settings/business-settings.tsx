"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Check } from "lucide-react";
import { type CSSProperties, useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { SelectField } from "@/components/forms/fields";
import { FormField } from "@/components/forms/form-field";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import type { Schemas } from "@/lib/api/client";
import { useBusiness, useUpdateBusiness } from "@/lib/agenda";
import { BRAND_PRESETS, contrastWithWhite, isHexColor, isReadableOnWhite } from "@/lib/brand";
import { useCanEdit } from "@/lib/catalog";
import { useMe } from "@/lib/session";
import { cn } from "@/lib/utils";
import {
  type BusinessData,
  type BusinessValues,
  businessSchema,
  timeZoneOptions,
  toBusinessValues,
  toUpdateBusinessRequest,
} from "@/lib/validation/business";

const FIELDS = ["name", "contactEmail", "timezone", "brandColor"] as const;

/** Ajustes del negocio. Solo el dueño puede cambiarlos (la API devuelve 403 al empleado). */
export function BusinessSettings() {
  const me = useMe();
  const canEdit = useCanEdit();
  const business = useBusiness();

  if (me.isPending) return <p role="status">Cargando…</p>;
  if (!canEdit) {
    return (
      <Alert>
        <AlertDescription>Solo el dueño del negocio puede cambiar los ajustes.</AlertDescription>
      </Alert>
    );
  }
  if (business.isPending) return <p role="status">Cargando…</p>;
  if (business.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudieron cargar los ajustes. Recarga la página.</AlertDescription>
      </Alert>
    );
  }
  // La `key` vuelve a montar el formulario si cambia el negocio (p. ej. al cambiar de sesión).
  return <SettingsForm key={business.data.id} business={business.data} />;
}

function SettingsForm({ business }: { business: Schemas["BusinessResponse"] }) {
  const update = useUpdateBusiness();
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    control,
    reset,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<BusinessValues, unknown, BusinessData>({
    resolver: zodResolver(businessSchema),
    defaultValues: toBusinessValues(business),
  });

  const onSubmit = handleSubmit(async (data) => {
    setFormError(null);
    setSaved(false);
    try {
      const updated = await update.mutateAsync(toUpdateBusinessRequest(data));
      reset(toBusinessValues(updated));
      setSaved(true);
    } catch (error) {
      setFormError(applyServerErrors(error, FIELDS, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-6">
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <Card>
        <CardHeader>
          <CardTitle>Tu negocio</CardTitle>
          <CardDescription>El nombre sale en tu página de reservas y en los emails a tus clientes.</CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <FormField label="Nombre" error={errors.name?.message} {...register("name")} />
          <FormField
            label="Email de contacto"
            type="email"
            hint="Aquí te avisamos de cada reserva, cambio o cancelación."
            error={errors.contactEmail?.message}
            {...register("contactEmail")}
          />
          <SelectField
            label="Zona horaria"
            hint="Los horarios y los huecos se calculan en esta zona."
            error={errors.timezone?.message}
            {...register("timezone")}
          >
            {timeZoneOptions(business.timezone).map((zone) => (
              <option key={zone} value={zone}>
                {zone.replaceAll("_", " ")}
              </option>
            ))}
          </SelectField>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Color de marca</CardTitle>
          <CardDescription>
            Es el único color de tu página de reservas: botones, selección y enlaces. Tiene que leerse bien
            sobre blanco.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Controller
            control={control}
            name="brandColor"
            render={({ field, fieldState }) => (
              <BrandColorPicker
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={fieldState.error?.message}
              />
            )}
          />
        </CardContent>
      </Card>

      <div className="flex items-center justify-end gap-4">
        {saved && !isDirty && (
          <p role="status" className="text-muted-foreground text-sm">
            Cambios guardados.
          </p>
        )}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Guardando…" : "Guardar cambios"}
        </Button>
      </div>
    </form>
  );
}

const ratio = new Intl.NumberFormat("es-ES", { maximumFractionDigits: 1 });

function BrandColorPicker({
  value,
  onChange,
  onBlur,
  error,
}: {
  value: string;
  onChange: (value: string) => void;
  onBlur: () => void;
  error?: string;
}) {
  const id = useId();
  const readable = isReadableOnWhite(value.trim());
  const current = value.trim().toLowerCase();

  return (
    <div className="grid gap-6 sm:grid-cols-[1fr_auto]">
      <div className="grid content-start gap-4">
        <div role="group" aria-label="Colores sugeridos" className="flex flex-wrap gap-2">
          {BRAND_PRESETS.map((preset) => {
            const selected = preset.color === current;
            return (
              <button
                key={preset.color}
                type="button"
                aria-label={preset.name}
                aria-pressed={selected}
                title={preset.name}
                onClick={() => onChange(preset.color)}
                className={cn(
                  "grid size-9 place-items-center rounded-full text-white outline-offset-2 focus-visible:outline-2",
                  selected && "ring-foreground ring-2 ring-offset-2",
                )}
                style={{ backgroundColor: preset.color, outlineColor: preset.color }}
              >
                {selected && <Check className="size-4" aria-hidden />}
              </button>
            );
          })}
        </div>
        <div className="grid gap-2">
          <label htmlFor={id} className="text-sm leading-none font-medium">
            Código del color
          </label>
          <div className="flex items-center gap-2">
            <input
              type="color"
              aria-label="Elegir otro color"
              value={isHexColor(current) ? current : "#000000"}
              onChange={(event) => onChange(event.target.value)}
              className="border-input h-9 w-12 cursor-pointer rounded-md border bg-transparent p-1"
            />
            <Input
              id={id}
              value={value}
              onChange={(event) => onChange(event.target.value)}
              onBlur={onBlur}
              spellCheck={false}
              autoComplete="off"
              className="w-32 font-mono"
              aria-invalid={error ? true : undefined}
              aria-describedby={`${id}-help`}
            />
          </div>
          <p id={`${id}-help`} className={cn("text-sm", error ? "text-destructive" : "text-muted-foreground")}>
            {error ??
              (readable ? `Contraste con el blanco: ${ratio.format(contrastWithWhite(current))}:1 (mínimo 4,5:1).` : "")}
          </p>
        </div>
      </div>
      <BrandPreview color={readable ? current : null} />
    </div>
  );
}

/** Lo que verá el cliente: un hueco elegido y el botón de reservar. */
function BrandPreview({ color }: { color: string | null }) {
  return (
    <figure className="grid content-start gap-2">
      <figcaption className="text-muted-foreground text-sm">Vista previa</figcaption>
      <div
        className="grid w-56 gap-3 rounded-lg border p-4"
        style={color ? ({ "--primary": color, "--ring": color } as CSSProperties) : undefined}
      >
        <div className="flex gap-2 text-sm" aria-hidden>
          <span className="rounded-md border px-3 py-1.5 tabular-nums">10:00</span>
          <span className="border-primary ring-primary/30 rounded-md border px-3 py-1.5 font-medium tabular-nums ring-2">
            10:30
          </span>
          <span className="rounded-md border px-3 py-1.5 tabular-nums">11:00</span>
        </div>
        <span
          aria-hidden
          className="bg-primary text-primary-foreground inline-flex h-9 items-center justify-center rounded-md text-sm font-medium"
        >
          Reservar
        </span>
      </div>
    </figure>
  );
}
