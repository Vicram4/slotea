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

import { PageHeader } from "../page-header";
import { StaffForm } from "./staff-form";
import { StaffInitial } from "./staff-initial";
import { StaffServicesEditor } from "./staff-services-editor";

/** Ficha de un profesional: datos, servicios que hace, horario semanal y ausencias. */
export function StaffDetail({ staffId }: { staffId: string }) {
  const canEdit = useCanEdit();
  const staff = useStaffMember(staffId);

  const back = (
    <Link
      href="/panel/profesionales"
      className="text-muted-foreground hover:text-foreground flex w-fit items-center gap-1 text-sm"
    >
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
  const services = member.serviceIds.length;
  return (
    <>
      <PageHeader
        before={back}
        title={
          <span className="flex items-center gap-3">
            <StaffInitial name={member.displayName} muted={!member.active} className="size-10 text-base" />
            {member.displayName}
            {!member.active && (
              <Badge variant="outline" className="font-normal">
                No acepta reservas
              </Badge>
            )}
          </span>
        }
        description={[
          services === 0 ? "Sin servicios" : `${services} ${services === 1 ? "servicio" : "servicios"}`,
          member.userId ? "entra en el panel y ve su agenda" : "sin usuario del panel",
        ].join(" · ")}
      />
      <Tabs defaultValue={canEdit ? "datos" : "servicios"}>
        <TabsList variant="line" className="w-full justify-start gap-4 rounded-none border-b px-0 pb-1">
          {canEdit && <TabsTrigger value="datos" className="flex-none px-0">Datos</TabsTrigger>}
          <TabsTrigger value="servicios" className="flex-none px-0">
            Servicios
          </TabsTrigger>
          <TabsTrigger value="horario" className="flex-none px-0">
            Horario
          </TabsTrigger>
          <TabsTrigger value="ausencias" className="flex-none px-0">
            Ausencias
          </TabsTrigger>
        </TabsList>
        {canEdit && (
          <TabsContent value="datos" className="max-w-xl pt-6">
            <StaffForm key={member.id} staff={member} onSaved={() => undefined} />
          </TabsContent>
        )}
        <TabsContent value="servicios" className="max-w-xl pt-6">
          <StaffServicesEditor key={member.id} staff={member} />
        </TabsContent>
        <TabsContent value="horario" className="pt-6">
          <WeeklyHoursEditor staffId={member.id} />
        </TabsContent>
        <TabsContent value="ausencias" className="pt-6">
          <ClosuresPanel staffId={member.id} />
        </TabsContent>
      </Tabs>
    </>
  );
}
