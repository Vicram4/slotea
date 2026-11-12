"use client";

import { Plus } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { type Service, useCanEdit, useServices } from "@/lib/catalog";
import { formatDuration, formatPrice } from "@/lib/validation/catalog";

import { ServiceForm } from "./service-form";

/** Lista de servicios. El dueño los crea y edita en un diálogo; el empleado solo los ve. */
export function ServicesPage() {
  const canEdit = useCanEdit();
  const services = useServices();
  // undefined = diálogo cerrado; null = servicio nuevo.
  const [editing, setEditing] = useState<Service | null | undefined>(undefined);

  return (
    <>
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Servicios</h1>
          <p className="text-muted-foreground text-sm">Lo que tus clientes pueden reservar.</p>
        </div>
        {canEdit && (
          <Button onClick={() => setEditing(null)}>
            <Plus aria-hidden /> Nuevo servicio
          </Button>
        )}
      </header>

      {services.isPending && <p role="status">Cargando servicios…</p>}
      {services.isError && (
        <Alert variant="destructive">
          <AlertDescription>No se pudieron cargar los servicios. Recarga la página.</AlertDescription>
        </Alert>
      )}
      {services.data?.length === 0 && (
        <p className="text-muted-foreground">
          Todavía no hay servicios.{canEdit && " Crea el primero con «Nuevo servicio»."}
        </p>
      )}
      {services.data && services.data.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Servicio</TableHead>
              <TableHead>Duración</TableHead>
              <TableHead>Margen</TableHead>
              <TableHead>Precio</TableHead>
              <TableHead>Estado</TableHead>
              {canEdit && <TableHead className="sr-only">Acciones</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {services.data.map((service) => (
              <TableRow key={service.id}>
                <TableCell>
                  <div className="font-medium">{service.name}</div>
                  {service.description && (
                    <div className="text-muted-foreground max-w-xs truncate text-xs">{service.description}</div>
                  )}
                </TableCell>
                <TableCell>{formatDuration(service.durationMin)}</TableCell>
                <TableCell>{service.bufferMin ? formatDuration(service.bufferMin) : "—"}</TableCell>
                <TableCell>{formatPrice(service.priceCents)}</TableCell>
                <TableCell>
                  {service.active ? <Badge>Activo</Badge> : <Badge variant="secondary">Desactivado</Badge>}
                </TableCell>
                {canEdit && (
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Editar ${service.name}`}
                      onClick={() => setEditing(service)}
                    >
                      Editar
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
