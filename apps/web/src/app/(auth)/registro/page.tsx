import type { Metadata } from "next";
import Link from "next/link";

import { SignupForm } from "@/components/auth/signup-form";

export const metadata: Metadata = { title: "Registra tu negocio · Slotea" };

export default function SignupPage() {
  return (
    <section className="grid gap-6">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Registra tu negocio</h1>
        <p className="text-muted-foreground text-sm">Crea tu página de reservas en un minuto.</p>
      </div>
      {/* Slotea es una demo: que nadie lo confunda con un servicio en el que dejar datos reales. */}
      <p role="note" className="bg-muted/50 rounded-md border px-3 py-2.5 text-sm">
        <strong className="font-medium">Proyecto de demostración.</strong>{" "}
        <span className="text-muted-foreground">
          No lo uses para un negocio real ni con datos reales. Lo que se guarda y por qué, en{" "}
          <Link href="/privacidad" className="text-foreground underline underline-offset-4">
            privacidad
          </Link>
          .
        </span>
      </p>
      <SignupForm />
    </section>
  );
}
