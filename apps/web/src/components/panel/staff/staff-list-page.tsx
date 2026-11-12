"use client";

import { ChevronRight, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useCanEdit, useServices, useStaff } from "@/lib/catalog";

import { StaffForm } from "./staff-form";

/** Equipo del negocio. Cada profesional abre su ficha (datos, servicios, horario y ausencias). */
export function StaffListPage() {
  const router = useRouter();
  const canEdit = useCanEdit();
  const staff = useStaff();
  const services = useServices();
  const [creating, setCreating] = useState(false);

  const serviceName = new Map(services.data?.map((s) => [s.id, s.name]));

  return (
    <>
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Profesionales</h1>
          <p className="text-muted-foreground text-sm">Quién atiende, qué hace y cuándo trabaja.</p>
        </div>
        {canEdit && (
          <Button onClick={() => setCreating(true)}>
            <Plus aria-hidden /> Nuevo profesional
          </Button>
        )}
      </header>

      {staff.isPending && <p role="status">Cargando profesionales…</p>}
      {staff.isError && (
        <Alert variant="destructive">
          <AlertDescription>No se pudieron cargar los profesionales. Recarga la página.</AlertDescription>
        </Alert>
      )}
      {staff.data?.length === 0 && <p className="text-muted-foreground">Todavía no hay profesionales.</p>}
      {staff.data && staff.data.length > 0 && (
        <ul className="divide-y rounded-lg border">
          {staff.data.map((member) => (
            <li key={member.id}>
              <Link
                href={`/panel/profesionales/${member.id}`}
                className="hover:bg-accent flex items-center gap-4 p-4"
              >
                <div className="grid flex-1 gap-1">
                  <span className="flex items-center gap-2 font-medium">
                    {member.displayName}
                    {!member.active && <Badge variant="secondary">Desactivado</Badge>}
                  </span>
                  <span className="text-muted-foreground text-sm">
                    {member.serviceIds.length === 0
                      ? "Sin servicios asignados"
                      : member.serviceIds.map((id) => serviceName.get(id) ?? "…").join(", ")}
                  </span>
                </div>
                <ChevronRight className="text-muted-foreground size-4" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}

      <Dialog open={creating} onOpenChange={setCreating}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Nuevo profesional</DialogTitle>
            <DialogDescription>Después podrás indicar sus servicios y su horario.</DialogDescription>
          </DialogHeader>
          {creating && <StaffForm onSaved={(saved) => router.push(`/panel/profesionales/${saved.id}`)} />}
        </DialogContent>
      </Dialog>
    </>
  );
}
