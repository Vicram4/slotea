import { describe, expect, it } from "vitest";

import { customerSchema } from "./booking";

const valid = { fullName: "Laura Pérez", email: "laura@example.test", phone: "", notes: "" };

describe("customerSchema", () => {
  it("accepts a guest with only name and email, trimming spaces", () => {
    expect(customerSchema.parse({ ...valid, fullName: "  Laura ", email: " laura@example.test " })).toEqual({
      ...valid,
      fullName: "Laura",
    });
  });

  it("accepts the usual phone formats and rejects anything else", () => {
    for (const phone of ["600000000", "+34 600 000 000", "+1 212 555 0100"]) {
      expect(customerSchema.safeParse({ ...valid, phone }).success, phone).toBe(true);
    }
    for (const phone of ["abc", "12345", "600-000-000", "+34 600 000 000 000 000 0"]) {
      expect(customerSchema.safeParse({ ...valid, phone }).success, phone).toBe(false);
    }
  });

  it("requires a name and a valid email", () => {
    const result = customerSchema.safeParse({ ...valid, fullName: " ", email: "no-es-email" });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((i) => i.path[0])).toEqual(["fullName", "email"]);
  });
});
