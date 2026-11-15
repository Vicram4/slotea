import type { Metadata } from "next";

import { LoginForm } from "@/components/auth/login-form";

export const metadata: Metadata = { title: "Entrar · Slotea" };

export default function LoginPage() {
  return (
    <section className="grid gap-6">
      <div className="grid gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Entra en tu panel</h1>
        <p className="text-muted-foreground text-sm">Tu agenda, tu equipo y tu página de reservas.</p>
      </div>
      <LoginForm />
    </section>
  );
}
