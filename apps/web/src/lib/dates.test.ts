import { describe, expect, it } from "vitest";

import { addDays, formatLongDate, shortDay, todayIn } from "./dates";

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
});
