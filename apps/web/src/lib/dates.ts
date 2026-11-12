/**
 * Fechas de calendario como texto ISO (`"2027-01-11"`), sin hora ni zona. La API ya devuelve la
 * hora local del negocio formateada (`"10:15"`), así que el navegador no necesita una librería de
 * zonas horarias: solo saber qué día es «hoy» en la zona del negocio y sumar días.
 */

export type IsoDate = string;

/** Hoy en esa zona horaria (no en la del navegador). */
export function todayIn(timeZone: string, now: Date = new Date()): IsoDate {
  // en-CA formatea como AAAA-MM-DD.
  return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(
    now,
  );
}

/** Suma días a una fecha de calendario (en UTC, para que el cambio de hora no influya). */
export function addDays(date: IsoDate, days: number): IsoDate {
  const [y, m, d] = date.split("-").map(Number);
  return new Date(Date.UTC(y!, m! - 1, d! + days)).toISOString().slice(0, 10);
}

function asUtc(date: IsoDate): Date {
  return new Date(`${date}T00:00:00Z`);
}

const weekday = new Intl.DateTimeFormat("es-ES", { weekday: "short", timeZone: "UTC" });
const dayMonth = new Intl.DateTimeFormat("es-ES", { day: "numeric", month: "short", timeZone: "UTC" });
const longDate = new Intl.DateTimeFormat("es-ES", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
  timeZone: "UTC",
});

/** «lun.» y «11 ene» por separado, para la tira de días. */
export function shortDay(date: IsoDate): { weekday: string; dayMonth: string } {
  return { weekday: weekday.format(asUtc(date)), dayMonth: dayMonth.format(asUtc(date)) };
}

/** «lunes, 11 de enero de 2027». */
export function formatLongDate(date: IsoDate): string {
  return longDate.format(asUtc(date));
}

/** ¿El navegador está en otra zona horaria? Entonces hay que avisar de que las horas son las del negocio. */
export function isOtherTimeZone(timeZone: string): boolean {
  const own = Intl.DateTimeFormat().resolvedOptions().timeZone;
  if (own === timeZone) return false;
  // Dos nombres distintos pueden ser la misma hora ahora mismo (Europe/Paris y Europe/Madrid).
  const now = new Date();
  const hour = (tz: string) =>
    new Intl.DateTimeFormat("en-GB", { timeZone: tz, hour: "2-digit", minute: "2-digit" }).format(now);
  return hour(own) !== hour(timeZone);
}
