"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";

import { ClosuresPanel } from "@/components/panel/schedule/closures-panel";
import { WeeklyHoursEditor } from "@/components/panel/schedule/weekly-hours-editor";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ApiError } from "@/lib/api/client";
import { useCanEdit, useStaffMember } from "@/lib/catalog";

import { StaffForm } from "./staff-form";
import { StaffServicesEditor } from "./staff-services-editor";

/** Ficha de un profesional: datos, servicios que hace, horario semanal y ausencias. */
export function StaffDetail({ staffId }: { staffId: string }) {
  const canEdit = useCanEdit();
  const staff = useStaffMember(staffId);

  const back = (
    <Link href="/panel/profesionales" className="text-muted-foreground flex items-center gap-1 text-sm">
      <ArrowLeft className="size-4" aria-hidden /> Profesionales
    </Link>
  );

  if (staff.isPending) return <p role="status">Cargando…</p>;
  if (staff.isError) {
    const notFound = staff.error instanceof ApiError && staff.error.status === 404;
    return (
      <>
        {back}
        <Alert variant="destructive">
          <AlertDescription>
            {notFound ? "Este profesional no existe." : "No se pudo cargar el profesional. Recarga la página."}
          </AlertDescription>
        </Alert>
      </>
    );
  }

  const member = staff.data;
  return (
    <>
      {back}
      <header className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">{member.displayName}</h1>
        {!member.active && <Badge variant="secondary">Desactivado</Badge>}
      </header>
      <Tabs defaultValue={canEdit ? "datos" : "servicios"}>
        <TabsList>
          {canEdit && <TabsTrigger value="datos">Datos</TabsTrigger>}
          <TabsTrigger value="servicios">Servicios</TabsTrigger>
          <TabsTrigger value="horario">Horario</TabsTrigger>
          <TabsTrigger value="ausencias">Ausencias</TabsTrigger>
        </TabsList>
        {canEdit && (
          <TabsContent value="datos" className="pt-4">
            <StaffForm key={member.id} staff={member} onSaved={() => undefined} />
          </TabsContent>
        )}
        <TabsContent value="servicios" className="pt-4">
          <StaffServicesEditor key={member.id} staff={member} />
        </TabsContent>
        <TabsContent value="horario" className="pt-4">
          <WeeklyHoursEditor staffId={member.id} />
        </TabsContent>
        <TabsContent value="ausencias" className="pt-4">
          <ClosuresPanel staffId={member.id} />
        </TabsContent>
      </Tabs>
    </>
  );
}
