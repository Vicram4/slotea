import { describe, expect, it } from "vitest";

import {
  closureSchema,
  formatDateRange,
  formatDuration,
  parsePrice,
  serviceSchema,
  slotErrors,
  todayIso,
  toServiceRequest,
  toServiceValues,
} from "./catalog";

describe("service form values", () => {
  it("accepts prices with comma or dot and converts them to cents", () => {
    expect(parsePrice("15")).toBe(1500);
    expect(parsePrice("15,5")).toBe(1550);
    expect(parsePrice("9.99")).toBe(999);
    expect(parsePrice("  ")).toBeNull();
  });

  it("round-trips a service through the form", () => {
    const values = toServiceValues({
      id: "x",
      name: "Corte",
      description: null,
      durationMin: 45,
      bufferMin: 5,
      priceCents: 1250,
      active: false,
      createdAt: "2026-11-01T10:00:00Z",
    });
    expect(values).toEqual({
      name: "Corte",
      description: "",
      durationMin: "45",
      bufferMin: "5",
      price: "12,50",
      active: false,
    });
    expect(toServiceRequest(values)).toEqual({
      name: "Corte",
      description: null,
      durationMin: 45,
      bufferMin: 5,
      priceCents: 1250,
      active: false,
    });
  });

  it("applies the same rules as the API", () => {
    const result = serviceSchema.safeParse({
      name: " ",
      description: "",
      durationMin: "32",
      bufferMin: "125",
      price: "15,555",
      active: true,
    });
    expect(result.success).toBe(false);
    const fields = result.error?.issues.map((issue) => issue.path[0]);
    expect(fields).toEqual(expect.arrayContaining(["name", "durationMin", "bufferMin", "price"]));
  });

  it("formats durations and date ranges in Spanish", () => {
    expect(formatDuration(30)).toBe("30 min");
    expect(formatDuration(60)).toBe("1 h");
    expect(formatDuration(90)).toBe("1 h 30 min");
    expect(formatDateRange("2026-12-24", "2026-12-24")).toBe("24 dic 2026");
    expect(formatDateRange("2026-12-24", "2026-12-26")).toBe("24 dic 2026 – 26 dic 2026");
  });

  it("uses the local date for today", () => {
    expect(todayIso(new Date(2026, 0, 5, 23, 30))).toBe("2026-01-05");
  });
});

describe("slotErrors", () => {
  it("accepts split shifts and contiguous slots in any order", () => {
    expect(
      slotErrors([
        { dayOfWeek: 1, start: "16:00", end: "20:00" },
        { dayOfWeek: 1, start: "09:00", end: "16:00" },
        { dayOfWeek: 2, start: "09:00", end: "13:30" },
      ]).size,
    ).toBe(0);
  });

  it("flags the slot that overlaps, is reversed, incomplete or off the 5-minute grid", () => {
    const errors = slotErrors([
      { dayOfWeek: 1, start: "09:00", end: "14:00" },
      { dayOfWeek: 1, start: "13:00", end: "18:00" },
      { dayOfWeek: 2, start: "18:00", end: "09:00" },
      { dayOfWeek: 3, start: "", end: "14:00" },
      { dayOfWeek: 4, start: "09:02", end: "14:00" },
    ]);
    expect(Object.fromEntries(errors)).toEqual({
      1: "Se solapa con otro tramo del mismo día.",
      2: "El fin debe ser posterior al inicio.",
      3: "Completa la hora de inicio y de fin.",
      4: "Usa intervalos de 5 minutos.",
    });
  });
});

describe("closureSchema", () => {
  it("requires the end date on or after the start date", () => {
    const result = closureSchema.safeParse({ staffId: "", startDate: "2026-12-26", endDate: "2026-12-24", reason: "" });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(["endDate"]);
    expect(
      closureSchema.safeParse({ staffId: "", startDate: "2026-12-24", endDate: "2026-12-24", reason: "" }).success,
    ).toBe(true);
  });
});
