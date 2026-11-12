import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, type Schemas, unwrap } from "@/lib/api/client";
import { useMe } from "@/lib/session";

export type Service = Schemas["ServiceResponse"];
export type ServiceRequest = Schemas["ServiceRequest"];
export type StaffMember = Schemas["StaffResponse"];
export type StaffRequest = Schemas["StaffRequest"];
export type TimeSlot = Schemas["TimeSlot"];
export type Closure = Schemas["ClosureResponse"];
export type ClosureRequest = Schemas["ClosureRequest"];
export type Member = Schemas["MemberResponse"];

/**
 * Claves de caché del catálogo. Todo cuelga de `["catalog", ...]`: al cambiar de sesión se puede
 * invalidar entero, y cada mutación invalida solo lo que ha cambiado.
 */
export const catalogKeys = {
  services: ["catalog", "services"] as const,
  staff: ["catalog", "staff"] as const,
  staffMember: (id: string) => ["catalog", "staff", id] as const,
  hours: (staffId: string) => ["catalog", "staff", staffId, "hours"] as const,
  closures: (params: ClosureFilter) => ["catalog", "closures", params] as const,
  allClosures: ["catalog", "closures"] as const,
  members: ["catalog", "members"] as const,
};

/** Solo el dueño cambia el catálogo; el empleado lo ve en solo lectura (la API devuelve 403). */
export function useCanEdit(): boolean {
  return useMe().data?.activeBusiness?.role === "OWNER";
}

// --- Servicios ---------------------------------------------------------------------------------

export function useServices() {
  return useQuery({
    queryKey: catalogKeys.services,
    queryFn: async () => unwrap(await api.GET("/api/v1/services")),
  });
}

export function useSaveService() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id?: string; body: ServiceRequest }) =>
      id
        ? unwrap(await api.PUT("/api/v1/services/{id}", { params: { path: { id } }, body }))
        : unwrap(await api.POST("/api/v1/services", { body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.services }),
  });
}

// --- Profesionales -----------------------------------------------------------------------------

export function useStaff() {
  return useQuery({
    queryKey: catalogKeys.staff,
    queryFn: async () => unwrap(await api.GET("/api/v1/staff")),
  });
}

export function useStaffMember(id: string) {
  return useQuery({
    queryKey: catalogKeys.staffMember(id),
    queryFn: async () => unwrap(await api.GET("/api/v1/staff/{id}", { params: { path: { id } } })),
  });
}

export function useSaveStaff() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, body }: { id?: string; body: StaffRequest }) =>
      id
        ? unwrap(await api.PUT("/api/v1/staff/{id}", { params: { path: { id } }, body }))
        : unwrap(await api.POST("/api/v1/staff", { body })),
    onSuccess: (saved) => {
      queryClient.setQueryData(catalogKeys.staffMember(saved.id), saved);
      return queryClient.invalidateQueries({ queryKey: catalogKeys.staff, exact: true });
    },
  });
}

export function useSaveStaffServices(staffId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (serviceIds: string[]) =>
      unwrap(
        await api.PUT("/api/v1/staff/{id}/services", { params: { path: { id: staffId } }, body: { serviceIds } }),
      ),
    onSuccess: (saved) => {
      queryClient.setQueryData(catalogKeys.staffMember(saved.id), saved);
      return queryClient.invalidateQueries({ queryKey: catalogKeys.staff, exact: true });
    },
  });
}

/** Miembros del negocio, para enlazar una ficha con un usuario. Solo el dueño puede pedirlos. */
export function useMembers(enabled: boolean) {
  return useQuery({
    queryKey: catalogKeys.members,
    queryFn: async () => unwrap(await api.GET("/api/v1/business/members")),
    enabled,
  });
}

// --- Horario semanal ---------------------------------------------------------------------------

export function useWeeklyHours(staffId: string) {
  return useQuery({
    queryKey: catalogKeys.hours(staffId),
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/staff/{staffId}/working-hours", { params: { path: { staffId } } })),
  });
}

export function useSaveWeeklyHours(staffId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (slots: TimeSlot[]) =>
      unwrap(
        await api.PUT("/api/v1/staff/{staffId}/working-hours", { params: { path: { staffId } }, body: { slots } }),
      ),
    onSuccess: (saved) => queryClient.setQueryData(catalogKeys.hours(staffId), saved),
  });
}

// --- Cierres -----------------------------------------------------------------------------------

export type ClosureFilter = { staffId?: string; from?: string };

export function useClosures(filter: ClosureFilter) {
  return useQuery({
    queryKey: catalogKeys.closures(filter),
    queryFn: async () => unwrap(await api.GET("/api/v1/closures", { params: { query: filter } })),
  });
}

export function useCreateClosure() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: ClosureRequest) => unwrap(await api.POST("/api/v1/closures", { body })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.allClosures }),
  });
}

export function useDeleteClosure() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const result = await api.DELETE("/api/v1/closures/{id}", { params: { path: { id } } });
      if (!result.response.ok) unwrap(result);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: catalogKeys.allClosures }),
  });
}
