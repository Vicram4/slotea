import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, type Schemas, unwrap } from "@/lib/api/client";
import type { IsoDate } from "@/lib/dates";
import { meQueryKey } from "@/lib/session";

export type Agenda = Schemas["AgendaResponse"];
export type AgendaAppointment = Schemas["AgendaAppointment"];
export type AgendaStaff = Schemas["AgendaStaff"];
export type AppointmentStatus = AgendaAppointment["status"];
/** Cómo acabó una cita: lo que se puede marcar (CONFIRMED = deshacer). */
export type Outcome = Exclude<AppointmentStatus, "CANCELLED">;

export type AgendaFilter = { from: IsoDate; to: IsoDate; staffId?: string };

export const agendaKeys = {
  all: ["agenda"] as const,
  range: (filter: AgendaFilter) => ["agenda", filter] as const,
  business: ["business"] as const,
};

export const statusLabels: Record<AppointmentStatus, string> = {
  CONFIRMED: "Confirmada",
  COMPLETED: "Completada",
  NO_SHOW: "No se presentó",
  CANCELLED: "Cancelada",
};

/** El negocio activo (sobre todo, su zona horaria: «hoy» es el de allí, no el del navegador). */
export function useBusiness() {
  return useQuery({
    queryKey: agendaKeys.business,
    queryFn: async () => unwrap(await api.GET("/api/v1/business")),
    staleTime: 5 * 60_000,
  });
}

/**
 * Cambia los datos del negocio (solo el dueño). El nombre también sale en la sesión (la barra del
 * panel), así que se vuelve a pedir.
 */
export function useUpdateBusiness() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: Schemas["UpdateBusinessRequest"]) => unwrap(await api.PATCH("/api/v1/business", { body })),
    onSuccess: (business) => {
      queryClient.setQueryData(agendaKeys.business, business);
      return queryClient.invalidateQueries({ queryKey: meQueryKey });
    },
  });
}

/**
 * Citas de un periodo. Se refresca sola cada minuto y al volver a la pestaña (las reservas llegan
 * mientras el negocio tiene la agenda abierta). Al cambiar de día se mantiene la anterior hasta que
 * llega la nueva, para que la rejilla no parpadee.
 */
export function useAgenda(filter: AgendaFilter, enabled = true) {
  return useQuery({
    queryKey: agendaKeys.range(filter),
    queryFn: async () => unwrap(await api.GET("/api/v1/appointments", { params: { query: filter } })),
    enabled,
    refetchInterval: 60_000,
    placeholderData: keepPreviousData,
  });
}

/** Tras cambiar una cita, se vuelven a pedir todas las agendas en caché (puede salir en varias). */
function useAgendaMutation<V>(mutationFn: (variables: V) => Promise<AgendaAppointment>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSettled: () => queryClient.invalidateQueries({ queryKey: agendaKeys.all }),
  });
}

export function useRecordOutcome() {
  return useAgendaMutation(async ({ id, status }: { id: string; status: Outcome }) =>
    unwrap(await api.PUT("/api/v1/appointments/{id}/status", { params: { path: { id } }, body: { status } })),
  );
}

export function useCancelAppointment() {
  return useAgendaMutation(async (id: string) =>
    unwrap(await api.POST("/api/v1/appointments/{id}/cancel", { params: { path: { id } } })),
  );
}

/** «09:30» → minutos desde las 00:00. */
export function toMinutes(time: string): number {
  const [h, m] = time.split(":").map(Number);
  return h! * 60 + m!;
}

/** Fin de la cita en minutos; una cita que acaba a las 00:00 acaba al final del día, no al principio. */
export function endMinutes(appointment: Pick<AgendaAppointment, "startTime" | "endTime">): number {
  const end = toMinutes(appointment.endTime);
  return end <= toMinutes(appointment.startTime) ? 24 * 60 : end;
}
