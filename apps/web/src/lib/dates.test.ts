import { describe, expect, it } from "vitest";

import {
  addDays,
  formatLongDate,
  formatMonthSpan,
  formatWeekRange,
  isIsoDate,
  minutesNowIn,
  shortDay,
  startOfWeek,
  todayIn,
} from "./dates";

describe("dates", () => {
  it("knows which day it is in the business time zone, not the browser's", () => {
    // 23:30 en UTC del 10 de enero: en Madrid ya es día 11; en Nueva York aún es día 10.
    const now = new Date("2027-01-10T23:30:00Z");
    expect(todayIn("Europe/Madrid", now)).toBe("2027-01-11");
    expect(todayIn("America/New_York", now)).toBe("2027-01-10");
  });

  it("adds days across months, years and daylight saving changes", () => {
    expect(addDays("2027-01-31", 1)).toBe("2027-02-01");
    expect(addDays("2026-12-28", 7)).toBe("2027-01-04");
    expect(addDays("2027-03-27", 1)).toBe("2027-03-28"); // cambio de hora en Madrid
    expect(addDays("2027-03-28", 1)).toBe("2027-03-29");
    expect(addDays("2027-01-08", -7)).toBe("2027-01-01");
  });

  it("formats dates in Spanish without shifting the day", () => {
    expect(formatLongDate("2027-01-11")).toBe("lunes, 11 de enero de 2027");
    expect(shortDay("2027-03-28")).toEqual({ weekday: "dom", dayMonth: "28 mar" });
  });

  it("finds the Monday of the week", () => {
    expect(startOfWeek("2026-11-23")).toBe("2026-11-23"); // lunes
    expect(startOfWeek("2026-11-29")).toBe("2026-11-23"); // domingo
    expect(startOfWeek("2027-01-01")).toBe("2026-12-28"); // viernes, semana entre dos años
  });

  it("accepts only real calendar dates from the URL", () => {
    expect(isIsoDate("2026-11-23")).toBe(true);
    expect(isIsoDate("2026-02-30")).toBe(false);
    expect(isIsoDate("23/11/2026")).toBe(false);
    expect(isIsoDate(null)).toBe(false);
  });

  it("tells the current time in the business time zone", () => {
    const now = new Date("2026-10-25T09:15:00Z"); // primer día del horario de invierno en Madrid
    expect(minutesNowIn("Europe/Madrid", now)).toBe(10 * 60 + 15);
    expect(minutesNowIn("UTC", new Date("2026-11-23T00:05:00Z"))).toBe(5);
  });

  it("formats a week range compactly", () => {
    expect(formatWeekRange("2026-11-23")).toBe("23 – 29 nov 2026");
    expect(formatWeekRange("2026-11-30")).toBe("30 nov – 6 dic 2026");
    expect(formatWeekRange("2026-12-28")).toBe("28 dic 2026 – 3 ene 2027");
  });

  it("names the month of a week, or both when it spans two", () => {
    expect(formatMonthSpan("2026-11-16", "2026-11-22")).toBe("noviembre de 2026");
    expect(formatMonthSpan("2026-11-30", "2026-12-06")).toBe("noviembre – diciembre de 2026");
    expect(formatMonthSpan("2026-12-28", "2027-01-03")).toBe("diciembre de 2026 – enero de 2027");
  });
});
