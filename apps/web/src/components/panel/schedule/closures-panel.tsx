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
import { cn } from "@/lib/utils";
import {
  useCanEdit,
  useClosures,
  useCreateClosure,
  useDeleteClosure,
  useOwnStaffId,
  useStaff,
} from "@/lib/catalog";
import { closureSchema, formatDateRange, todayIso, type ClosureValues } from "@/lib/validation/catalog";

const FIELDS = ["staffId", "startDate", "endDate", "reason"] as const;

/**
 * Próximos cierres (de hoy en adelante). Sin `staffId`: los de todo el negocio y las ausencias de
 * cada profesional. Con `staffId`: solo las ausencias de ese profesional.
 *
 * El dueño lo gestiona todo; un empleado, solo sus propias ausencias (el resto, en solo lectura).
 */
export function ClosuresPanel({ staffId }: { staffId?: string }) {
  const isOwner = useCanEdit();
  const ownStaffId = useOwnStaffId();
  // El empleado añade ausencias para sí mismo: en su ficha o en la página de cierres, nunca en la de otro.
  const formStaffId = isOwner ? staffId : ownStaffId && (!staffId || staffId === ownStaffId) ? ownStaffId : null;
  const canDelete = (closureStaffId: string | null | undefined) =>
    isOwner || (!!ownStaffId && closureStaffId === ownStaffId);
  const [today] = useState(todayIso);
  const closures = useClosures({ staffId, from: today });
  const staff = useStaff();
  const remove = useDeleteClosure();

  const staffName = new Map(staff.data?.map((s) => [s.id, s.displayName]));
  const scopeOf = (id: string | null | undefined) =>
    id ? (staffName.get(id) ?? "Un profesional") : "Todo el negocio";

  return (
    <div
      className={cn(
        "grid items-start gap-6",
        formStaffId !== null && (staffId ? "xl:grid-cols-[1fr_20rem]" : "lg:grid-cols-[1fr_20rem]"),
      )}
    >
      <section className="grid gap-3" aria-labelledby="closures-title">
        <h2 id="closures-title" className="text-sm font-medium">
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
        {closures.data?.length === 0 && (
          <p className="text-muted-foreground rounded-lg border border-dashed p-8 text-center text-sm">
            No hay nada previsto.
          </p>
        )}
        {closures.data && closures.data.length > 0 && (
          <ul className="divide-y rounded-lg border">
            {closures.data.map((closure) => {
              const when = formatDateRange(closure.startDate, closure.endDate);
              return (
                <li key={closure.id} className="flex items-center gap-4 px-4 py-3">
                  <CalendarDay iso={closure.startDate} />
                  <div className="grid flex-1 gap-0.5">
                    <span className="flex flex-wrap items-baseline gap-x-2">
                      <span className="font-medium">{when}</span>
                      <span className="text-muted-foreground text-xs">{formatDays(closure)}</span>
                    </span>
                    <span className="text-muted-foreground text-sm">
                      {!staffId && `${scopeOf(closure.staffId)}`}
                      {!staffId && closure.reason && " · "}
                      {closure.reason}
                    </span>
                  </div>
                  {canDelete(closure.staffId) && (
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
      {formStaffId !== null && <ClosureForm staffId={formStaffId} />}
    </div>
  );
}

const monthShort = new Intl.DateTimeFormat("es-ES", { month: "short", timeZone: "UTC" });
const utc = (iso: string) => new Date(`${iso}T00:00:00Z`);

/** «1 día», «3 días»: las fechas son de calendario, incluido el último. */
function formatDays({ startDate, endDate }: { startDate: string; endDate: string }): string {
  const days = Math.round((utc(endDate).getTime() - utc(startDate).getTime()) / 86_400_000) + 1;
  return days === 1 ? "1 día" : `${days} días`;
}

/** El primer día como una hoja de calendario: el mes arriba, el día grande. */
function CalendarDay({ iso }: { iso: string }) {
  const date = utc(iso);
  return (
    <span aria-hidden className="grid w-11 shrink-0 overflow-hidden rounded-md border text-center">
      <span className="bg-muted text-muted-foreground py-0.5 text-[0.65rem] uppercase">
        {monthShort.format(date).replace(".", "")}
      </span>
      <span className="py-0.5 text-base font-semibold tabular-nums">{date.getUTCDate()}</span>
    </span>
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
    <form onSubmit={onSubmit} noValidate className="bg-muted/30 grid gap-4 rounded-lg border p-4">
      <div className="grid gap-1">
        <h2 className="text-sm font-medium">{staffId ? "Añadir una ausencia" : "Añadir un cierre"}</h2>
        <p className="text-muted-foreground text-xs">Esos días no saldrán huecos en tu página de reservas.</p>
      </div>
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
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1 2xl:grid-cols-2">
        <FormField label="Desde" type="date" error={errors.startDate?.message} {...register("startDate")} />
        <FormField label="Hasta (incluido)" type="date" error={errors.endDate?.message} {...register("endDate")} />
      </div>
      <FormField
        label="Motivo (opcional)"
        placeholder={staffId ? "Vacaciones, formación…" : "Festivo, vacaciones…"}
        error={errors.reason?.message}
        {...register("reason")}
      />
      <Button type="submit" disabled={isSubmitting} className="w-full">
        {isSubmitting ? "Guardando…" : "Añadir"}
      </Button>
    </form>
  );
}
