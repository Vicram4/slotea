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
import { cn } from "@/lib/utils";

import { PageHeader } from "../page-header";
import { StaffForm } from "./staff-form";
import { StaffInitial } from "./staff-initial";

/** Equipo del negocio. Cada profesional abre su ficha (datos, servicios, horario y ausencias). */
export function StaffListPage() {
  const router = useRouter();
  const canEdit = useCanEdit();
  const staff = useStaff();
  const services = useServices();
  const [creating, setCreating] = useState(false);

  const serviceName = new Map(services.data?.map((s) => [s.id, s.name]));

  const list = staff.data ?? [];
  const inactive = list.filter((m) => !m.active).length;

  return (
    <>
      <PageHeader
        title="Profesionales"
        description={
          list.length > 0
            ? `${list.length} en el equipo${inactive > 0 ? ` (${inactive} sin aceptar reservas)` : ""}. Abre una ficha para elegir qué hace cada uno y cuándo trabaja.`
            : "Quién atiende, qué hace y cuándo trabaja."
        }
        actions={
          canEdit && (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden /> Nuevo profesional
            </Button>
          )
        }
      />

      {staff.isPending && <p role="status">Cargando profesionales…</p>}
      {staff.isError && (
        <Alert variant="destructive">
          <AlertDescription>No se pudieron cargar los profesionales. Recarga la página.</AlertDescription>
        </Alert>
      )}
      {staff.data?.length === 0 && (
        <div className="text-muted-foreground rounded-lg border border-dashed p-10 text-center text-sm">
          Todavía no hay profesionales.{canEdit && " Añade al primero con «Nuevo profesional»."}
        </div>
      )}
      {list.length > 0 && (
        <ul className="divide-y rounded-lg border">
          {list.map((member) => (
            <li key={member.id}>
              <Link
                href={`/panel/profesionales/${member.id}`}
                className="hover:bg-muted/50 flex items-center gap-4 px-4 py-3 first:rounded-t-lg"
              >
                <StaffInitial name={member.displayName} muted={!member.active} />
                <div className="grid min-w-0 flex-1 gap-0.5">
                  <span className="flex items-center gap-2 font-medium">
                    <span className={cn(!member.active && "text-muted-foreground")}>{member.displayName}</span>
                    {!member.active && (
                      <Badge variant="outline" className="font-normal">
                        No acepta reservas
                      </Badge>
                    )}
                  </span>
                  <span className="text-muted-foreground truncate text-sm">
                    {member.serviceIds.length === 0
                      ? "Sin servicios: todavía no se le puede reservar"
                      : member.serviceIds.map((id) => serviceName.get(id) ?? "…").join(", ")}
                  </span>
                </div>
                {member.userId && (
                  <span className="text-muted-foreground hidden text-xs sm:inline">Entra en el panel</span>
                )}
                <ChevronRight className="text-muted-foreground size-4 shrink-0" aria-hidden />
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
