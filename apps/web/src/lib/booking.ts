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

/** Mismos límites que la API (BookingPolicy.java): la tira de días no deja ir más allá. */
export const HORIZON_DAYS = 60;

export const bookingKeys = {
  business: (slug: string) => ["public", slug] as const,
  availability: (slug: string) => ["public", slug, "availability"] as const,
  week: (slug: string, serviceId: string, staffId: string | null, from: IsoDate) =>
    ["public", slug, "availability", serviceId, staffId, from] as const,
};

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
