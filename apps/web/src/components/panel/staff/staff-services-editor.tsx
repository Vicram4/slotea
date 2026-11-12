"use client";

import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { type StaffMember, useCanEdit, useSaveStaffServices, useServices } from "@/lib/catalog";
import { formatDuration } from "@/lib/validation/catalog";

/** Qué servicios hace un profesional: casillas que se guardan juntas (la API sustituye el conjunto). */
export function StaffServicesEditor({ staff }: { staff: StaffMember }) {
  const canEdit = useCanEdit();
  const services = useServices();
  const save = useSaveStaffServices(staff.id);
  const [selected, setSelected] = useState<Set<string>>(() => new Set(staff.serviceIds));

  if (services.isPending) return <p role="status">Cargando servicios…</p>;
  if (services.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudieron cargar los servicios.</AlertDescription>
      </Alert>
    );
  }
  // Los desactivados solo aparecen si ya estaban asignados (para poder quitarlos).
  const visible = services.data.filter((s) => s.active || staff.serviceIds.includes(s.id));
  if (visible.length === 0) {
    return <p className="text-muted-foreground">Todavía no hay servicios. Créalos en «Servicios».</p>;
  }

  const toggle = (id: string, checked: boolean) => {
    save.reset();
    setSelected((current) => {
      const next = new Set(current);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  return (
    <form
      className="grid gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        save.mutate(visible.filter((s) => selected.has(s.id)).map((s) => s.id));
      }}
    >
      <fieldset className="grid gap-3" disabled={!canEdit}>
        <legend className="sr-only">Servicios que hace {staff.displayName}</legend>
        {visible.map((service) => (
          <div key={service.id} className="flex items-center gap-3">
            <Checkbox
              id={`service-${service.id}`}
              checked={selected.has(service.id)}
              onCheckedChange={(checked) => toggle(service.id, checked === true)}
              disabled={!canEdit}
            />
            <Label htmlFor={`service-${service.id}`} className="font-normal">
              {service.name}
              <span className="text-muted-foreground">· {formatDuration(service.durationMin)}</span>
              {!service.active && <span className="text-muted-foreground">(desactivado)</span>}
            </Label>
          </div>
        ))}
      </fieldset>
      {canEdit && (
        <div className="flex items-center justify-end gap-3">
          {save.isSuccess && (
            <span role="status" className="text-muted-foreground text-sm">
              Servicios guardados.
            </span>
          )}
          {save.isError && <span className="text-destructive text-sm">No se pudo guardar. Inténtalo de nuevo.</span>}
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? "Guardando…" : "Guardar servicios"}
          </Button>
        </div>
      )}
    </form>
  );
}
