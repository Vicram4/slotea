"use client";

import { useEffect } from "react";

/**
 * La API escala a cero: un ping al llegar a la portada la despierta mientras el visitante lee y elige
 * sector, y la demo abre antes. No pinta nada; si falla, el lanzador ya avisa al crear la copia.
 */
export function WakeUpApi() {
  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/v1/ping", { signal: controller.signal }).catch(() => undefined);
    return () => controller.abort();
  }, []);
  return null;
}
