import { z } from "zod";

import type { Service, ServiceRequest, TimeSlot } from "@/lib/catalog";

/**
 * Mismas reglas que la API (ServiceDtos, ScheduleDtos, WeeklySchedule). La API valida igualmente:
 * esto solo da respuesta inmediata. Los formularios trabajan con texto (lo que hay en los `<input>`)
 * y se convierte al enviar.
 */

/** Las citas encajan en una rejilla de 5 minutos. */
export const STEP_MINUTES = 5;

function minutes(min: number, max: number) {
  return z
    .string()
    .trim()
    .regex(/^\d+$/, "Escribe un número de minutos.")
    .refine((value) => Number(value) >= min && Number(value) <= max, `Entre ${min} y ${max} minutos.`)
    .refine((value) => Number(value) % STEP_MINUTES === 0, "De 5 en 5 minutos.");
}

export const serviceSchema = z.object({
  name: z.string().trim().min(1, "Pon un nombre.").max(100, "Máximo 100 caracteres."),
  description: z.string().max(500, "Máximo 500 caracteres."),
  durationMin: minutes(5, 480),
  bufferMin: minutes(0, 120),
  price: z
    .string()
    .trim()
    .regex(/^(\d{1,5}([.,]\d{1,2})?)?$/, "Escribe un precio como 15 o 15,50."),
  active: z.boolean(),
});

export type ServiceValues = z.infer<typeof serviceSchema>;

export const emptyService: ServiceValues = {
  name: "",
  description: "",
  durationMin: "30",
  bufferMin: "0",
  price: "",
  active: true,
};

/** «15,5» → 1550 céntimos; vacío → sin precio. */
export function parsePrice(text: string): number | null {
  const value = text.trim();
  if (!value) return null;
  return Math.round(Number(value.replace(",", ".")) * 100);
}

export function toServiceRequest(values: ServiceValues): ServiceRequest {
  return {
    name: values.name.trim(),
    description: values.description.trim() || null,
    durationMin: Number(values.durationMin),
    bufferMin: Number(values.bufferMin),
    priceCents: parsePrice(values.price),
    active: values.active,
  };
}

export function toServiceValues(service: Service): ServiceValues {
  return {
    name: service.name,
    description: service.description ?? "",
    durationMin: String(service.durationMin),
    bufferMin: String(service.bufferMin),
    price: service.priceCents == null ? "" : formatPriceInput(service.priceCents),
    active: service.active,
  };
}

function formatPriceInput(cents: number): string {
  return cents % 100 === 0 ? String(cents / 100) : (cents / 100).toFixed(2).replace(".", ",");
}

const euros = new Intl.NumberFormat("es-ES", { style: "currency", currency: "EUR" });

export function formatPrice(cents: number | null | undefined): string {
  return cents == null ? "—" : euros.format(cents / 100);
}

/** 90 → «1 h 30 min». */
export function formatDuration(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const rest = totalMinutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

// --- Profesionales -----------------------------------------------------------------------------

export const staffSchema = z.object({
  displayName: z.string().trim().min(1, "Pon un nombre.").max(100, "Máximo 100 caracteres."),
  userId: z.string(),
  active: z.boolean(),
});

export type StaffValues = z.infer<typeof staffSchema>;

// --- Horario semanal ---------------------------------------------------------------------------

/** ISO-8601, igual que la API: 1 = lunes … 7 = domingo. */
export const DAYS = [
  { day: 1, name: "Lunes" },
  { day: 2, name: "Martes" },
  { day: 3, name: "Miércoles" },
  { day: 4, name: "Jueves" },
  { day: 5, name: "Viernes" },
  { day: 6, name: "Sábado" },
  { day: 7, name: "Domingo" },
] as const;

const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;

function toMinutes(time: string): number {
  const [hours, mins] = time.split(":").map(Number);
  return hours! * 60 + mins!;
}

/**
 * Errores de cada tramo (por su posición en `slots`): hora incompleta, fuera de la rejilla de
 * 5 minutos, fin antes del inicio o solape con otro tramo del mismo día. Mismas reglas que la API.
 */
export function slotErrors(slots: readonly TimeSlot[]): Map<number, string> {
  const errors = new Map<number, string>();
  slots.forEach((slot, index) => {
    if (!TIME.test(slot.start) || !TIME.test(slot.end)) {
      errors.set(index, "Completa la hora de inicio y de fin.");
    } else if (toMinutes(slot.start) % STEP_MINUTES !== 0 || toMinutes(slot.end) % STEP_MINUTES !== 0) {
      errors.set(index, "Usa intervalos de 5 minutos.");
    } else if (toMinutes(slot.end) <= toMinutes(slot.start)) {
      errors.set(index, "El fin debe ser posterior al inicio.");
    }
  });
  const valid = slots
    .map((slot, index) => ({ slot, index }))
    .filter(({ index }) => !errors.has(index))
    .sort((a, b) => a.slot.dayOfWeek - b.slot.dayOfWeek || toMinutes(a.slot.start) - toMinutes(b.slot.start));
  for (let k = 1; k < valid.length; k++) {
    const previous = valid[k - 1]!.slot;
    const current = valid[k]!;
    if (previous.dayOfWeek === current.slot.dayOfWeek && toMinutes(current.slot.start) < toMinutes(previous.end)) {
      errors.set(current.index, "Se solapa con otro tramo del mismo día.");
    }
  }
  return errors;
}

// --- Cierres -----------------------------------------------------------------------------------

export const closureSchema = z
  .object({
    staffId: z.string(),
    startDate: z.iso.date("Elige la fecha de inicio."),
    endDate: z.iso.date("Elige la fecha de fin."),
    reason: z.string().max(200, "Máximo 200 caracteres."),
  })
  .refine((values) => values.endDate >= values.startDate, {
    path: ["endDate"],
    message: "Debe ser igual o posterior a la fecha de inicio.",
  });

export type ClosureValues = z.infer<typeof closureSchema>;

const dateFormat = new Intl.DateTimeFormat("es-ES", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });

/** «2026-12-24», «2026-12-26» → «24 dic 2026 – 26 dic 2026» (un solo día: «24 dic 2026»). */
export function formatDateRange(start: string, end: string): string {
  const format = (iso: string) => dateFormat.format(new Date(`${iso}T00:00:00Z`));
  return start === end ? format(start) : `${format(start)} – ${format(end)}`;
}

/** Fecha local de hoy en ISO («2026-11-12»), sin pasar por UTC. */
export function todayIso(now = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}
