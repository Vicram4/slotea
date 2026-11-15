import type { Metadata } from "next";

import { SignupForm } from "@/components/auth/signup-form";

export const metadata: Metadata = { title: "Registra tu negocio · Slotea" };

export default function SignupPage() {
  return (
    <section className="grid gap-6">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Registra tu negocio</h1>
        <p className="text-muted-foreground text-sm">Crea tu página de reservas en un minuto.</p>
      </div>
      <SignupForm />
    </section>
  );
}
