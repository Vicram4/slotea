"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Trash2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";

import { SelectField } from "@/components/forms/fields";
import { FormField } from "@/components/forms/form-field";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useCanEdit, useClosures, useCreateClosure, useDeleteClosure, useStaff } from "@/lib/catalog";
import { closureSchema, formatDateRange, todayIso, type ClosureValues } from "@/lib/validation/catalog";

const FIELDS = ["staffId", "startDate", "endDate", "reason"] as const;

/**
 * Próximos cierres (de hoy en adelante). Sin `staffId`: los de todo el negocio y las ausencias de
 * cada profesional. Con `staffId`: solo las ausencias de ese profesional.
 */
export function ClosuresPanel({ staffId }: { staffId?: string }) {
  const canEdit = useCanEdit();
  const [today] = useState(todayIso);
  const closures = useClosures({ staffId, from: today });
  const staff = useStaff();
  const remove = useDeleteClosure();

  const staffName = new Map(staff.data?.map((s) => [s.id, s.displayName]));
  const scopeOf = (id: string | null | undefined) =>
    id ? (staffName.get(id) ?? "Un profesional") : "Todo el negocio";

  return (
    <div className="grid gap-6">
      {canEdit && <ClosureForm staffId={staffId} />}
      <section className="grid gap-3" aria-labelledby="closures-title">
        <h2 id="closures-title" className="font-medium">
          {staffId ? "Próximas ausencias" : "Próximos cierres"}
        </h2>
        {closures.isPending && <p role="status">Cargando…</p>}
        {closures.isError && (
          <Alert variant="destructive">
            <AlertDescription>No se pudieron cargar los cierres.</AlertDescription>
          </Alert>
        )}
        {remove.isError && (
          <Alert variant="destructive">
            <AlertDescription>No se pudo borrar. Inténtalo de nuevo.</AlertDescription>
          </Alert>
        )}
        {closures.data?.length === 0 && <p className="text-muted-foreground text-sm">No hay nada previsto.</p>}
        {closures.data && closures.data.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {closures.data.map((closure) => {
              const when = formatDateRange(closure.startDate, closure.endDate);
              return (
                <li key={closure.id} className="flex items-center gap-4 p-3">
                  <div className="grid flex-1 gap-0.5">
                    <span className="font-medium">{when}</span>
                    <span className="text-muted-foreground text-sm">
                      {!staffId && `${scopeOf(closure.staffId)}`}
                      {!staffId && closure.reason && " · "}
                      {closure.reason}
                    </span>
                  </div>
                  {canEdit && (
                    <Button
                      variant="ghost"
                      size="icon"
                      disabled={remove.isPending}
                      aria-label={`Borrar cierre del ${when}`}
                      onClick={() => remove.mutate(closure.id)}
                    >
                      <Trash2 aria-hidden />
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </section>
    </div>
  );
}

function ClosureForm({ staffId }: { staffId?: string }) {
  const create = useCreateClosure();
  const staff = useStaff();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ClosureValues>({
    resolver: zodResolver(closureSchema),
    defaultValues: { staffId: staffId ?? "", startDate: "", endDate: "", reason: "" },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await create.mutateAsync({
        staffId: values.staffId || null,
        startDate: values.startDate,
        endDate: values.endDate,
        reason: values.reason.trim() || null,
      });
      reset();
    } catch (error) {
      setFormError(applyServerErrors(error, FIELDS, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4 rounded-lg border p-4">
      <h2 className="font-medium">{staffId ? "Añadir una ausencia" : "Añadir un cierre"}</h2>
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      {!staffId && (
        <SelectField label="Afecta a" error={errors.staffId?.message} {...register("staffId")}>
          <option value="">Todo el negocio</option>
          {staff.data
            ?.filter((s) => s.active)
            .map((s) => (
              <option key={s.id} value={s.id}>
                {s.displayName}
              </option>
            ))}
        </SelectField>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        <FormField label="Desde" type="date" error={errors.startDate?.message} {...register("startDate")} />
        <FormField label="Hasta (incluido)" type="date" error={errors.endDate?.message} {...register("endDate")} />
      </div>
      <FormField
        label="Motivo (opcional)"
        placeholder={staffId ? "Vacaciones, formación…" : "Festivo, vacaciones…"}
        error={errors.reason?.message}
        {...register("reason")}
      />
      <Button type="submit" disabled={isSubmitting} className="justify-self-end">
        {isSubmitting ? "Guardando…" : "Añadir"}
      </Button>
    </form>
  );
}
