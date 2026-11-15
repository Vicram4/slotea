import { useEffect } from "react";

/**
 * Color de marca de cada negocio. Mismas reglas que la API (`@BrandColor`): `#rrggbb` con un
 * contraste de al menos 4,5:1 frente al blanco (WCAG AA), para que sirva de fondo de un botón con
 * texto blanco y de texto o borde sobre la página. La API valida igualmente: esto es para avisar ya.
 */

/** El de un negocio que no ha elegido ninguno (el mismo que pone la migración V8). */
export const DEFAULT_BRAND_COLOR = "#18181b";

export const MIN_CONTRAST = 4.5;

/** Las muestras del selector: el neutro y el color de cada sector de la demo. */
export const BRAND_PRESETS = [
  { color: DEFAULT_BRAND_COLOR, name: "Grafito" },
  { color: "#1e3a8a", name: "Azul marino" },
  { color: "#be185d", name: "Frambuesa" },
  { color: "#c2410c", name: "Naranja óxido" },
  { color: "#0f766e", name: "Verde azulado" },
  { color: "#6d28d9", name: "Violeta" },
  { color: "#15803d", name: "Verde" },
] as const;

const HEX = /^#[0-9a-f]{6}$/i;

export function isHexColor(value: string): boolean {
  return HEX.test(value);
}

/** Contraste WCAG entre el color y el blanco: de 1 (blanco) a 21 (negro). */
export function contrastWithWhite(hex: string): number {
  if (!isHexColor(hex)) throw new Error(`Not a #rrggbb color: ${hex}`);
  const rgb = Number.parseInt(hex.slice(1), 16);
  const channel = (shift: number) => {
    const c = ((rgb >> shift) & 0xff) / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  const luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0);
  return 1.05 / (luminance + 0.05);
}

export function isReadableOnWhite(hex: string): boolean {
  return isHexColor(hex) && contrastWithWhite(hex) >= MIN_CONTRAST;
}

const BRAND_VARIABLES = ["--primary", "--primary-foreground", "--ring"] as const;

/**
 * Pinta la página con el color del negocio: los componentes ya usan `bg-primary`, `ring-primary`…
 * Se aplica en `<html>` (y no en un contenedor) para que llegue también a los diálogos, que se
 * abren en un portal fuera del árbol. Al salir de la página se quita.
 */
export function useBrandColor(color: string | undefined) {
  useEffect(() => {
    if (!color || !isReadableOnWhite(color)) return;
    const style = document.documentElement.style;
    style.setProperty("--primary", color);
    style.setProperty("--primary-foreground", "#ffffff");
    style.setProperty("--ring", color);
    return () => BRAND_VARIABLES.forEach((name) => style.removeProperty(name));
  }, [color]);
}
