import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api, type Me, type Schemas, unwrap } from "@/lib/api/client";
import { meQueryKey } from "@/lib/session";

export type DemoRole = Schemas["DemoSessionRequest"]["role"];
export type InboxEmail = Schemas["InboxEmail"];
export type InboxEmailDetail = Schemas["InboxEmailDetail"];

/** Desde dónde se entra en la demo. El cliente no tiene cuenta: entra como dueño y va a la página pública. */
export type DemoEntry = "OWNER" | "STAFF" | "CUSTOMER";

/** Datos de prueba del formulario de reserva (dominio reservado para ejemplos: nunca es una persona real). */
export const DEMO_CUSTOMER = { fullName: "Cliente Demo", email: "cliente.demo@example.com" } as const;

export const demoKeys = {
  inbox: ["demo", "inbox"] as const,
  email: (id: string) => ["demo", "inbox", id] as const,
};

/** Adónde lleva cada entrada una vez creada la copia. */
export function demoLanding(entry: DemoEntry, me: Me): string {
  if (entry === "CUSTOMER" && me.activeBusiness) return `/${me.activeBusiness.slug}`;
  return entry === "STAFF" ? "/panel/agenda" : "/panel";
}

/** Al cambiar de usuario, nada de la sesión anterior puede quedar en caché (como en el logout). */
function replaceSession(queryClient: ReturnType<typeof useQueryClient>, me: Me) {
  queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== meQueryKey[0] });
  queryClient.setQueryData(meQueryKey, me);
}

/** Crea una copia de la demo para este visitante y abre sesión en ella. */
export function useStartDemo() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (entry: DemoEntry) => {
      const role: DemoRole = entry === "STAFF" ? "STAFF" : "OWNER";
      return unwrap(await api.POST("/api/v1/demo/sessions", { body: { role } }));
    },
    onSuccess: (me) => replaceSession(queryClient, me),
  });
}

/** Pasa de dueño a empleado (o al revés) dentro de la misma copia. */
export function useSwitchDemoRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (role: DemoRole) => unwrap(await api.POST("/api/v1/demo/switch", { body: { role } })),
    onSuccess: (me) => replaceSession(queryClient, me),
  });
}

/** Los emails que habría mandado la copia. Se refresca al volver a la pestaña (tras reservar como cliente). */
export function useDemoInbox() {
  return useQuery({
    queryKey: demoKeys.inbox,
    queryFn: async () => unwrap(await api.GET("/api/v1/demo/inbox")),
    refetchOnWindowFocus: "always",
  });
}

export function useDemoEmail(id: string | null) {
  return useQuery({
    queryKey: demoKeys.email(id ?? ""),
    queryFn: async () => unwrap(await api.GET("/api/v1/demo/inbox/{id}", { params: { path: { id: id! } } })),
    enabled: id !== null,
    staleTime: Infinity, // un email no cambia
  });
}
