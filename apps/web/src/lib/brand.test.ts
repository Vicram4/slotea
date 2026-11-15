import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { BRAND_PRESETS, contrastWithWhite, isReadableOnWhite, useBrandColor } from "./brand";

describe("brand color contrast", () => {
  it("follows the WCAG formula, like the API", () => {
    expect(contrastWithWhite("#ffffff")).toBeCloseTo(1, 3);
    expect(contrastWithWhite("#000000")).toBeCloseTo(21, 3);
    expect(contrastWithWhite("#767676")).toBeCloseTo(4.54, 2);
    expect(contrastWithWhite("#777777")).toBeCloseTo(4.48, 2);
  });

  it("only accepts #rrggbb colors that read well on white", () => {
    expect(isReadableOnWhite("#767676")).toBe(true);
    expect(isReadableOnWhite("#1E3A8A")).toBe(true);
    expect(isReadableOnWhite("#777777")).toBe(false);
    expect(isReadableOnWhite("#ffd700")).toBe(false);
    expect(isReadableOnWhite("#fff")).toBe(false);
    expect(isReadableOnWhite("navy")).toBe(false);
  });

  it("offers only readable, distinct presets", () => {
    const colors = BRAND_PRESETS.map((p) => p.color);
    expect(new Set(colors).size).toBe(colors.length);
    expect(colors.every(isReadableOnWhite)).toBe(true);
  });
});

describe("useBrandColor", () => {
  const style = document.documentElement.style;

  afterEach(() => style.removeProperty("--primary"));

  it("paints the page with the color and cleans up when leaving", () => {
    const { rerender, unmount } = renderHook(({ color }) => useBrandColor(color), {
      initialProps: { color: undefined as string | undefined },
    });
    expect(style.getPropertyValue("--primary")).toBe("");

    rerender({ color: "#0f766e" });
    expect(style.getPropertyValue("--primary")).toBe("#0f766e");
    expect(style.getPropertyValue("--ring")).toBe("#0f766e");
    expect(style.getPropertyValue("--primary-foreground")).toBe("#ffffff");

    unmount();
    expect(style.getPropertyValue("--primary")).toBe("");
    expect(style.getPropertyValue("--ring")).toBe("");
  });

  it("ignores a color that would not read well", () => {
    renderHook(() => useBrandColor("#ffd700"));
    expect(style.getPropertyValue("--primary")).toBe("");
  });
});
