"use client";

import { useEffect, useState } from "react";

type Status =
  | { kind: "loading" }
  | { kind: "ok"; time: string }
  | { kind: "error" };

/** Comprueba que el navegador llega a la API a través del proxy /api de Next. */
export function ApiStatus() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    fetch("/api/v1/ping", { signal: controller.signal })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<{ time: string }>;
      })
      .then((body) => setStatus({ kind: "ok", time: body.time }))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          console.error(error);
          setStatus({ kind: "error" });
        }
      });
    return () => controller.abort();
  }, []);

  return (
    <p role="status" className="rounded-md border px-4 py-3 text-sm">
      {status.kind === "loading" && "Conectando con el servidor…"}
      {status.kind === "ok" && `Servidor conectado (${new Date(status.time).toLocaleString("es-ES")})`}
      {status.kind === "error" && "No se pudo conectar con el servidor."}
    </p>
  );
}
