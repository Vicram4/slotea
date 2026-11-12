import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, type Me, type Schemas, unwrap } from "@/lib/api/client";

export const meQueryKey = ["me"] as const;

/**
 * Sesión actual. `null` = no hay sesión (o ha caducado y el refresh no ha podido renovarla).
 * El refresh lo hace `authFetch` por debajo, así que aquí un 401 ya es definitivo.
 */
export function useMe() {
  return useQuery({
    queryKey: meQueryKey,
    queryFn: async (): Promise<Me | null> => {
      const result = await api.GET("/api/v1/me");
      if (result.response.status === 401) return null;
      return unwrap(result);
    },
    retry: false,
    staleTime: 60_000,
  });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: Schemas["LoginRequest"]) => unwrap(await api.POST("/api/v1/auth/login", { body })),
    onSuccess: (me) => queryClient.setQueryData(meQueryKey, me),
  });
}

export function useSignup() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: Schemas["SignupRequest"]) => unwrap(await api.POST("/api/v1/auth/signup", { body })),
    onSuccess: (me) => queryClient.setQueryData(meQueryKey, me),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.POST("/api/v1/auth/logout");
    },
    // Aunque la API falle, en este navegador la sesión se da por cerrada (las cookies ya no sirven).
    onSettled: () => {
      queryClient.setQueryData(meQueryKey, null);
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== meQueryKey[0] });
    },
  });
}

export const roleLabels: Record<Schemas["MembershipSummary"]["role"], string> = {
  OWNER: "Dueño",
  STAFF: "Empleado",
};
