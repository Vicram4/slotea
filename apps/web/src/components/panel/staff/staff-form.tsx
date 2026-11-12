"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";

import { SelectField } from "@/components/forms/fields";
import { FormField } from "@/components/forms/form-field";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { type StaffMember, useMembers, useSaveStaff } from "@/lib/catalog";
import { staffSchema, type StaffValues } from "@/lib/validation/catalog";

const FIELDS = ["displayName", "userId", "active"] as const;

/** Alta o edición de un profesional (solo el dueño: necesita la lista de miembros). */
export function StaffForm({ staff, onSaved }: { staff?: StaffMember; onSaved: (saved: StaffMember) => void }) {
  const save = useSaveStaff();
  const members = useMembers(true);
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<StaffValues>({
    resolver: zodResolver(staffSchema),
    defaultValues: {
      displayName: staff?.displayName ?? "",
      userId: staff?.userId ?? "",
      active: staff?.active ?? true,
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    setSaved(false);
    try {
      const result = await save.mutateAsync({
        id: staff?.id,
        body: { displayName: values.displayName.trim(), userId: values.userId || null, active: values.active },
      });
      setSaved(true);
      onSaved(result);
    } catch (error) {
      setFormError(applyServerErrors(error, FIELDS, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <FormField
        label="Nombre visible"
        hint="Es el que verán tus clientes al reservar."
        error={errors.displayName?.message}
        {...register("displayName")}
      />
      <SelectField
        label="Usuario del panel (opcional)"
        hint="Enlázalo si esta persona entra en Slotea; así verá su propia agenda."
        error={errors.userId?.message}
        disabled={members.isPending}
        {...register("userId")}
      >
        <option value="">Sin usuario</option>
        {members.data?.map((member) => (
          <option key={member.userId} value={member.userId}>
            {member.fullName} ({member.email})
          </option>
        ))}
      </SelectField>
      {staff && (
        <Controller
          control={control}
          name="active"
          render={({ field }) => (
            <div className="flex items-center gap-3">
              <Switch id="staff-active" checked={field.value} onCheckedChange={field.onChange} />
              <Label htmlFor="staff-active">Acepta reservas</Label>
            </div>
          )}
        />
      )}
      <div className="flex items-center justify-end gap-3">
        {saved && staff && (
          <span role="status" className="text-muted-foreground text-sm">
            Cambios guardados.
          </span>
        )}
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Guardando…" : staff ? "Guardar cambios" : "Crear profesional"}
        </Button>
      </div>
    </form>
  );
}
