import type { Metadata } from "next";
import Link from "next/link";
import { Suspense } from "react";

import { ManageAppointmentPage } from "@/components/booking/manage-appointment-page";

/**
 * El enlace del email para gestionar una cita sin cuenta. La URL lleva el token, así que:
 * `no-referrer` (no sale a otras webs al pulsar un enlace) y `noindex` (no aparece en buscadores).
 */
export const metadata: Metadata = {
  title: "Tu cita · Slotea",
  referrer: "no-referrer",
  robots: { index: false, follow: false },
};

export default function Page({ params }: PageProps<"/[slug]/cita/[token]">) {
  return (
    <main className="bg-muted/50 flex min-h-dvh flex-col gap-8 px-4 py-8 sm:px-6 sm:py-16">
      <div className="mx-auto grid w-full max-w-4xl gap-4">
        {/* Con cacheComponents, `params` es un dato de la petición: se espera dentro de un Suspense. */}
        <Suspense fallback={<p role="status">Cargando…</p>}>
          <ManageRoute params={params} />
        </Suspense>
      </div>
      <footer className="text-muted-foreground mt-auto text-center text-xs">
        Reservas con{" "}
        <Link href="/" className="underline-offset-4 hover:underline">
          Slotea
        </Link>
        {" · "}
        <Link href="/privacidad" className="underline-offset-4 hover:underline">
          Privacidad
        </Link>
      </footer>
    </main>
  );
}

async function ManageRoute({ params }: { params: PageProps<"/[slug]/cita/[token]">["params"] }) {
  const { slug, token } = await params;
  return <ManageAppointmentPage slug={slug} token={token} />;
}
