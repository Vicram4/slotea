import { z } from "zod";

import type { Schemas } from "@/lib/api/client";
import { isHexColor, isReadableOnWhite } from "@/lib/brand";

type Business = Schemas["BusinessResponse"];
type UpdateBusinessRequest = Schemas["UpdateBusinessRequest"];

/**
 * Mismas reglas que la API (BusinessDtos.UpdateBusinessRequest). La API valida igualmente: esto solo
 * da respuesta inmediata.
 */
export const businessSchema = z.object({
  name: z.string().trim().min(1, "Pon un nombre.").max(100, "Máximo 100 caracteres."),
  contactEmail: z.string().trim().pipe(z.email("Introduce un email válido.").max(254, "El email es demasiado largo.")),
  timezone: z.string().min(1, "Elige una zona horaria."),
  brandColor: z
    .string()
    .trim()
    .refine(isHexColor, "Escribe un color como #1e3a8a.")
    .refine(
      (value) => !isHexColor(value) || isReadableOnWhite(value),
      "Es demasiado claro: no se leería bien sobre blanco. Prueba con uno más oscuro.",
    ),
});

export type BusinessValues = z.input<typeof businessSchema>;
export type BusinessData = z.output<typeof businessSchema>;

export function toBusinessValues(business: Business): BusinessValues {
  return {
    name: business.name,
    contactEmail: business.contactEmail,
    timezone: business.timezone,
    brandColor: business.brandColor,
  };
}

export function toUpdateBusinessRequest(data: BusinessData): UpdateBusinessRequest {
  return { ...data, brandColor: data.brandColor.toLowerCase() };
}

/**
 * Zonas IANA de región (como exige la API: con `/`, para que el cambio de hora funcione), con la
 * actual siempre incluida aunque el navegador no la conozca.
 */
export function timeZoneOptions(current: string): string[] {
  const zones = Intl.supportedValuesOf("timeZone").filter((zone) => zone.includes("/"));
  return zones.includes(current) ? zones : [current, ...zones];
}
