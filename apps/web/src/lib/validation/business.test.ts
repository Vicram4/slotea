import { describe, expect, it } from "vitest";

import { business } from "@/test/agenda-fixtures";

import { businessSchema, timeZoneOptions, toBusinessValues, toUpdateBusinessRequest } from "./business";

function brandColorError(brandColor: string) {
  const result = businessSchema.safeParse({ ...toBusinessValues(business), brandColor });
  return result.success ? null : result.error.issues.find((i) => i.path[0] === "brandColor")?.message;
}

describe("business settings form", () => {
  it("accepts readable colors in any case and sends them in lowercase", () => {
    expect(brandColorError("#1E3A8A")).toBeNull();
    const data = businessSchema.parse({ ...toBusinessValues(business), brandColor: " #1E3A8A ", name: " Barbería " });
    expect(toUpdateBusinessRequest(data)).toEqual({
      name: "Barbería",
      contactEmail: business.contactEmail,
      timezone: "Europe/Madrid",
      brandColor: "#1e3a8a",
    });
  });

  it("explains why a color is rejected", () => {
    expect(brandColorError("navy")).toBe("Escribe un color como #1e3a8a.");
    expect(brandColorError("#ffd700")).toMatch(/demasiado claro/);
  });

  it("offers region time zones and always the current one", () => {
    const zones = timeZoneOptions("Europe/Madrid");
    expect(zones).toContain("Atlantic/Canary");
    expect(zones.every((zone) => zone.includes("/"))).toBe(true);
    expect(timeZoneOptions("Antarctica/Unknown")[0]).toBe("Antarctica/Unknown");
  });
});
