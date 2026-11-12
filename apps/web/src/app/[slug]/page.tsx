import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { BookingPage } from "@/components/booking/booking-page";

export const metadata: Metadata = { title: "Reservar cita · Slotea" };

/**
 * Página pública de reservas de cada negocio. Las rutas fijas (`/panel`, `/login`…) tienen
 * prioridad, y la API no deja registrar esos nombres como slug (RESERVED_SLUGS en AuthService).
 */
export default function Page({ params }: PageProps<"/[slug]">) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-2xl flex-col gap-8 px-6 py-10">
      {/* Con cacheComponents, `params` es un dato de la petición: se espera dentro de un Suspense. */}
      <Suspense fallback={<p role="status">Cargando…</p>}>
        <BookingRoute params={params} />
      </Suspense>
      <footer className="text-muted-foreground mt-auto text-center text-xs">
        Reservas con{" "}
        <Link href="/" className="underline-offset-4 hover:underline">
          Slotea
        </Link>
      </footer>
    </main>
  );
}

async function BookingRoute({ params }: { params: PageProps<"/[slug]">["params"] }) {
  const { slug } = await params;
  return <BookingPage slug={slug} />;
}
