import { describe, expect, it } from "vitest";

import { signupSchema, slugify } from "./auth";

describe("slugify", () => {
  it.each([
    ["Barbería Paco", "barberia-paco"],
    ["  Peluquería Ana & Co. ", "peluqueria-ana-co"],
    ["Ñandú 24h", "nandu-24h"],
    ["---", ""],
  ])("turns %j into %j", (input, expected) => {
    expect(slugify(input)).toBe(expected);
  });

  it("never exceeds 50 characters nor ends with a dash", () => {
    const slug = slugify(`${"a".repeat(49)} b`);
    expect(slug.length).toBeLessThanOrEqual(50);
    expect(slug.endsWith("-")).toBe(false);
  });
});

describe("signupSchema", () => {
  const valid = {
    businessName: "Barbería Demo",
    slug: "barberia-demo",
    fullName: "Ana Demo",
    email: "ana@example.test",
    password: "una-contraseña-larga",
  };

  it("accepts valid data", () => {
    expect(signupSchema.safeParse(valid).success).toBe(true);
  });

  it.each(["-mal", "mal-", "Mayus", "con espacio", "ab"])("rejects the slug %j", (slug) => {
    expect(signupSchema.safeParse({ ...valid, slug }).success).toBe(false);
  });

  it("rejects passwords longer than 72 bytes (BCrypt limit), counting multi-byte characters", () => {
    expect(signupSchema.safeParse({ ...valid, password: "ñ".repeat(37) }).success).toBe(false);
    expect(signupSchema.safeParse({ ...valid, password: "ñ".repeat(36) }).success).toBe(true);
  });
});
