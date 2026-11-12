"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { TextareaField } from "@/components/forms/fields";
import { FormField } from "@/components/forms/form-field";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { type Service, useSaveService } from "@/lib/catalog";
import {
  emptyService,
  serviceSchema,
  toServiceRequest,
  toServiceValues,
  type ServiceValues,
} from "@/lib/validation/catalog";

const FIELDS = ["name", "description", "durationMin", "bufferMin", "price", "active"] as const;

/** Alta o edición de un servicio. Al guardar llama a `onSaved` (p. ej. para cerrar el diálogo). */
export function ServiceForm({ service, onSaved }: { service?: Service; onSaved: () => void }) {
  const save = useSaveService();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<ServiceValues>({
    resolver: zodResolver(serviceSchema),
    defaultValues: service ? toServiceValues(service) : emptyService,
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await save.mutateAsync({ id: service?.id, body: toServiceRequest(values) });
      onSaved();
    } catch (error) {
      setFormError(applyServerErrors(error, FIELDS, setError, { priceCents: "price" }));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <FormField label="Nombre" error={errors.name?.message} {...register("name")} />
      <TextareaField
        label="Descripción (opcional)"
        rows={2}
        error={errors.description?.message}
        {...register("description")}
      />
      <div className="grid gap-4 sm:grid-cols-3">
        <FormField
          label="Duración (min)"
          inputMode="numeric"
          error={errors.durationMin?.message}
          {...register("durationMin")}
        />
        <FormField
          label="Margen después (min)"
          inputMode="numeric"
          hint="Para limpiar o preparar."
          error={errors.bufferMin?.message}
          {...register("bufferMin")}
        />
        <FormField
          label="Precio (€, opcional)"
          inputMode="decimal"
          error={errors.price?.message}
          {...register("price")}
        />
      </div>
      <Controller
        control={control}
        name="active"
        render={({ field }) => (
          <div className="flex items-center gap-3">
            <Switch id="service-active" checked={field.value} onCheckedChange={field.onChange} />
            <Label htmlFor="service-active">Se puede reservar</Label>
          </div>
        )}
      />
      <Button type="submit" disabled={isSubmitting} className="justify-self-end">
        {isSubmitting ? "Guardando…" : "Guardar servicio"}
      </Button>
    </form>
  );
}
