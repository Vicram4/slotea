import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, type Schemas, unwrap } from "@/lib/api/client";
import type { IsoDate } from "@/lib/dates";

export type PublicBusiness = Schemas["PublicBusinessResponse"];
export type PublicService = Schemas["PublicService"];
export type PublicStaff = Schemas["PublicStaff"];
export type Availability = Schemas["AvailabilityResponse"];
export type Slot = Schemas["SlotResponse"];
export type BookingRequest = Schemas["BookingRequest"];
export type Booking = Schemas["BookingResponse"];
export type ManagedAppointment = Schemas["ManagedAppointmentResponse"];
export type RescheduleRequest = Schemas["RescheduleRequest"];

/** Mismos límites que la API (BookingPolicy.java): la tira de días no deja ir más allá. */
export const HORIZON_DAYS = 60;

export const bookingKeys = {
  business: (slug: string) => ["public", slug] as const,
  availability: (slug: string) => ["public", slug, "availability"] as const,
  week: (slug: string, serviceId: string, staffId: string | null, from: IsoDate) =>
    ["public", slug, "availability", serviceId, staffId, from] as const,
  managed: (slug: string, token: string) => ["public", slug, "manage", token] as const,
  manageWeek: (slug: string, token: string, staffId: string | null, from: IsoDate) =>
    ["public", slug, "manage", token, "availability", staffId, from] as const,
};

/** Ruta de la página para gestionar una cita (el mismo enlace que llega por email). */
export function manageHref(slug: string, token: string) {
  return `/${slug}/cita/${token}`;
}

export function usePublicBusiness(slug: string) {
  return useQuery({
    queryKey: bookingKeys.business(slug),
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/public/businesses/{slug}", { params: { path: { slug } } })),
  });
}

/** Huecos de 7 días a partir de `from`. Al cambiar de semana se mantiene la anterior mientras carga. */
export function useWeekAvailability(
  slug: string,
  serviceId: string | null,
  staffId: string | null,
  from: IsoDate,
  to: IsoDate,
) {
  return useQuery({
    queryKey: bookingKeys.week(slug, serviceId ?? "", staffId, from),
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/public/businesses/{slug}/availability", {
          params: {
            path: { slug },
            query: { serviceId: serviceId!, from, to, ...(staffId ? { staffId } : {}) },
          },
        }),
      ),
    enabled: serviceId !== null,
    placeholderData: keepPreviousData,
  });
}

export function useBook(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: BookingRequest) =>
      unwrap(await api.POST("/api/v1/public/businesses/{slug}/appointments", { params: { path: { slug } }, body })),
    // Tanto si sale bien como si el hueco se acaba de ocupar (409), los huecos han cambiado.
    onSettled: () => queryClient.invalidateQueries({ queryKey: bookingKeys.availability(slug) }),
  });
}

// --- Enlace de gestión de la cita (sin cuenta) ------------------------------------------------

const MANAGE = "/api/v1/public/businesses/{slug}/appointments/manage/{token}" as const;

export function useManagedAppointment(slug: string, token: string) {
  return useQuery({
    queryKey: bookingKeys.managed(slug, token),
    queryFn: async () => unwrap(await api.GET(MANAGE, { params: { path: { slug, token } } })),
    retry: false, // un enlace inválido (404) no mejora reintentando
  });
}

/** Huecos para mover la cita (mismo servicio). La propia cita no cuenta como ocupada. */
export function useManageAvailability(
  slug: string,
  token: string,
  staffId: string | null,
  from: IsoDate,
  to: IsoDate,
  enabled: boolean,
) {
  return useQuery({
    queryKey: bookingKeys.manageWeek(slug, token, staffId, from),
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/public/businesses/{slug}/appointments/manage/{token}/availability", {
          params: { path: { slug, token }, query: { from, to, ...(staffId ? { staffId } : {}) } },
        }),
      ),
    enabled,
    placeholderData: keepPreviousData,
  });
}

function useManageMutation<TBody>(
  slug: string,
  token: string,
  request: (body: TBody) => Promise<ManagedAppointment>,
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (appointment) => queryClient.setQueryData(bookingKeys.managed(slug, token), appointment),
    // Haya ido bien o el hueco se haya ocupado (409), la cita y los huecos pueden haber cambiado.
    onSettled: () => queryClient.invalidateQueries({ queryKey: ["public", slug] }),
  });
}

export function useCancelAppointment(slug: string, token: string) {
  return useManageMutation<void>(slug, token, async () =>
    unwrap(await api.POST("/api/v1/public/businesses/{slug}/appointments/manage/{token}/cancel", { params: { path: { slug, token } } })),
  );
}

export function useRescheduleAppointment(slug: string, token: string) {
  return useManageMutation<RescheduleRequest>(slug, token, async (body) =>
    unwrap(await api.POST("/api/v1/public/businesses/{slug}/appointments/manage/{token}/reschedule", { params: { path: { slug, token } }, body })),
  );
}
