import { z } from "zod";

/**
 * Mismas reglas que la API (AuthDtos.java). La API valida igualmente: esto solo da respuesta
 * inmediata al usuario.
 */

const email = z.email("Introduce un email válido.").max(254, "El email es demasiado largo.");

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Introduce tu contraseña.").max(72, "La contraseña es demasiado larga."),
});

export type LoginValues = z.infer<typeof loginSchema>;

export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export const signupSchema = z.object({
  businessName: z.string().trim().min(1, "Pon el nombre de tu negocio.").max(100, "Máximo 100 caracteres."),
  slug: z
    .string()
    .min(3, "Mínimo 3 caracteres.")
    .max(50, "Máximo 50 caracteres.")
    .regex(SLUG_PATTERN, "Solo minúsculas, números y guiones (sin guion al principio ni al final)."),
  fullName: z.string().trim().min(1, "Pon tu nombre.").max(100, "Máximo 100 caracteres."),
  email,
  password: z
    .string()
    .min(10, "Mínimo 10 caracteres.")
    .refine((value) => new TextEncoder().encode(value).length <= 72, "La contraseña es demasiado larga."),
});

export type SignupValues = z.infer<typeof signupSchema>;

/** «Peluquería Ana & Co.» → «peluqueria-ana-co»: propuesta de dirección pública a partir del nombre. */
export function slugify(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 50)
    .replace(/-+$/g, "");
}
