import { Activity, Brain, PawPrint, Scissors, Sparkles, Wrench } from "lucide-react";
import type { ComponentType } from "react";

import type { DemoSector } from "@/lib/demo";

export type DemoSectorInfo = {
  name: string;
  /** Qué enseña la demo de este sector, con su propia voz. */
  tagline: string;
  icon: ComponentType<{ className?: string }>;
  /**
   * Si hay cuenta de empleado. La API es quien manda (400 si se pide), pero el lanzador tiene que
   * saberlo antes de crear la copia para no ofrecer un botón que no funciona.
   */
  hasStaff: boolean;
};

/**
 * Los sectores de la demo, en el orden de la portada. Es un `Record` sobre el tipo generado desde la
 * API: si allí se añade un sector, TypeScript obliga a darle textos aquí.
 */
export const DEMO_SECTORS: Record<DemoSector, DemoSectorInfo> = {
  BARBERSHOP: {
    name: "Barbería",
    tagline: "Tres barberos, cortes de 20 a 45 minutos y la agenda llena.",
    icon: Scissors,
    hasStaff: true,
  },
  HAIR_AND_BEAUTY: {
    name: "Peluquería y estética",
    tagline: "Un tinte de 2 h no cabe en un hueco de 30. La esteticista tiene su propio catálogo.",
    icon: Sparkles,
    hasStaff: true,
  },
  MECHANIC: {
    name: "Taller mecánico",
    tagline: "Revisiones de 2 horas y un margen entre citas para sacar el coche del elevador.",
    icon: Wrench,
    hasStaff: true,
  },
  PSYCHOLOGY: {
    name: "Psicología",
    tagline: "Una sola profesional, sesiones de 50 minutos y siempre a la hora en punto.",
    icon: Brain,
    hasStaff: false,
  },
  PHYSIOTHERAPY: {
    name: "Fisioterapia",
    tagline: "Tres fisios, con turnos de mañana y de tarde.",
    icon: Activity,
    hasStaff: true,
  },
  VETERINARY: {
    name: "Veterinaria",
    tagline: "Consultas cortas con tiempo para limpiar la sala, y peluquería canina aparte.",
    icon: PawPrint,
    hasStaff: true,
  },
};

export const DEMO_SECTOR_ORDER = Object.keys(DEMO_SECTORS) as DemoSector[];
