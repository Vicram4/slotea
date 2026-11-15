"use client";

import { Pencil, Plus } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { type Service, useCanEdit, useServices } from "@/lib/catalog";
import { cn } from "@/lib/utils";
import { formatDuration, formatPrice } from "@/lib/validation/catalog";

import { PageHeader } from "../page-header";
import { ServiceForm } from "./service-form";

/** «4 servicios, de 20 min a 1 h»: lo que hay de un vistazo. */
function summary(services: readonly Service[]): string {
  const count = `${services.length} ${services.length === 1 ? "servicio" : "servicios"}`;
  const durations = services.map((s) => s.durationMin);
  const [min, max] = [Math.min(...durations), Math.max(...durations)];
  return min === max
    ? `${count} de ${formatDuration(min)}`
    : `${count}, de ${formatDuration(min)} a ${formatDuration(max)}`;
}

/** Lista de servicios. El dueño los crea y edita en un diálogo; el empleado solo los ve. */
export function ServicesPage() {
  const canEdit = useCanEdit();
  const services = useServices();
  // undefined = diálogo cerrado; null = servicio nuevo.
  const [editing, setEditing] = useState<Service | null | undefined>(undefined);

  const list = services.data ?? [];
  const disabled = list.filter((s) => !s.active).length;

  return (
    <>
      <PageHeader
        title="Servicios"
        description={
          list.length > 0 ? (
            <>
              {summary(list)}
              {disabled > 0 && ` · ${disabled} ${disabled === 1 ? "desactivado" : "desactivados"}`}. La duración
              decide en qué huecos cabe cada uno: un tinte de 2 h no entra en uno de 30 min.
            </>
          ) : (
            "Lo que tus clientes pueden reservar."
          )
        }
        actions={
          canEdit && (
            <Button onClick={() => setEditing(null)}>
              <Plus aria-hidden /> Nuevo servicio
            </Button>
          )
        }
      />

      {services.isPending && <p role="status">Cargando servicios…</p>}
      {services.isError && (
        <Alert variant="destructive">
          <AlertDescription>No se pudieron cargar los servicios. Recarga la página.</AlertDescription>
        </Alert>
      )}
      {services.data?.length === 0 && (
        <div className="text-muted-foreground rounded-lg border border-dashed p-10 text-center text-sm">
          Todavía no hay servicios.{canEdit && " Crea el primero con «Nuevo servicio»."}
        </div>
      )}
      {list.length > 0 && (
        <div className="rounded-lg border">
          <Table>
            <TableHeader className="[&_th]:text-muted-foreground [&_th]:h-9 [&_th]:px-4 [&_th]:text-xs [&_th]:font-normal">
              <TableRow className="hover:bg-transparent">
                <TableHead>Servicio</TableHead>
                <TableHead className="max-sm:hidden">Duración</TableHead>
                <TableHead className="text-right max-sm:hidden">Precio</TableHead>
                {canEdit && (
                  <TableHead className="w-0">
                    <span className="sr-only">Acciones</span>
                  </TableHead>
                )}
              </TableRow>
            </TableHeader>
            <TableBody className="[&_td]:px-4 [&_td]:py-3">
              {list.map((service) => (
                <TableRow key={service.id} className={cn(!service.active && "text-muted-foreground")}>
                  <TableCell className="whitespace-normal">
                    <div className="flex items-center gap-2 font-medium">
                      <span className={cn(service.active && "text-foreground")}>{service.name}</span>
                      {!service.active && (
                        <Badge variant="outline" className="font-normal">
                          Desactivado
                        </Badge>
                      )}
                    </div>
                    {service.description && (
                      <div className="text-muted-foreground max-w-[12rem] truncate text-xs sm:max-w-md">{service.description}</div>
                    )}
                    {/* En el móvil no caben las columnas: duración y precio, debajo del nombre. */}
                    <div className="text-muted-foreground mt-1 text-xs tabular-nums sm:hidden">
                      {formatDuration(service.durationMin)} · {formatPrice(service.priceCents)}
                    </div>
                  </TableCell>
                  <TableCell className="tabular-nums max-sm:hidden">
                    {formatDuration(service.durationMin)}
                    {service.bufferMin > 0 && (
                      <span className="text-muted-foreground"> + {service.bufferMin} min de margen</span>
                    )}
                  </TableCell>
                  <TableCell className="text-right tabular-nums max-sm:hidden">{formatPrice(service.priceCents)}</TableCell>
                  {canEdit && (
                    <TableCell className="text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Editar ${service.name}`}
                        onClick={() => setEditing(service)}
                      >
                        <Pencil aria-hidden />
                        <span className="max-sm:hidden">Editar</span>
                      </Button>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={editing !== undefined} onOpenChange={(open) => !open && setEditing(undefined)}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{editing ? "Editar servicio" : "Nuevo servicio"}</DialogTitle>
            <DialogDescription>
              Los servicios no se borran: desactívalos para que no se puedan reservar.
            </DialogDescription>
          </DialogHeader>
          {editing !== undefined && (
            <ServiceForm
              key={editing?.id ?? "new"}
              service={editing ?? undefined}
              onSaved={() => setEditing(undefined)}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
