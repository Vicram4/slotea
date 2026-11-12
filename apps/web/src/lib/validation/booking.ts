import { z } from "zod";

/**
 * Mismas reglas que la API (PublicDtos.java). La API valida igualmente: esto solo da respuesta
 * inmediata al cliente.
 */

/** Opcional: «+34 600 000 000», «600000000»… (6 a 20 caracteres, cifras y espacios). */
export const PHONE_PATTERN = /^\+?[0-9][0-9 ]{5,18}$/;

export const customerSchema = z.object({
  fullName: z.string().trim().min(1, "Pon tu nombre.").max(100, "Máximo 100 caracteres."),
  email: z.string().trim().pipe(z.email("Introduce un email válido.").max(254, "El email es demasiado largo.")),
  phone: z
    .string()
    .trim()
    .refine((value) => value === "" || PHONE_PATTERN.test(value), "Introduce un teléfono válido (solo cifras)."),
  notes: z.string().trim().max(500, "Máximo 500 caracteres."),
});

export type CustomerValues = z.input<typeof customerSchema>;
export type CustomerData = z.output<typeof customerSchema>;
