import type { FieldValues, Path, UseFormSetError } from "react-hook-form";

import { ApiError } from "@/lib/api/client";

/**
 * Lleva los errores de la API al formulario: los de campo (400) a su campo y el resto como mensaje
 * general. Devuelve el mensaje general, si lo hay.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  fields: readonly Path<T>[],
  setError: UseFormSetError<T>,
): string | null {
  if (!(error instanceof ApiError)) {
    return "No se pudo conectar con el servidor. Inténtalo de nuevo.";
  }
  let assigned = false;
  for (const fieldError of error.problem.errors ?? []) {
    const field = fields.find((f) => f === fieldError.field);
    if (field) {
      setError(field, { type: "server", message: fieldError.message ?? "Valor no válido." });
      assigned = true;
    }
  }
  if (assigned) return null;
  return error.problem.detail ?? "Algo ha fallado. Inténtalo de nuevo.";
}
