import type { Metadata } from "next";

import { LoginForm } from "@/components/auth/login-form";

export const metadata: Metadata = { title: "Entrar · Slotea" };

export default function LoginPage() {
  return (
    <section className="grid gap-6">
      <h1 className="text-3xl font-semibold tracking-tight">Entra en tu panel</h1>
      <LoginForm />
    </section>
  );
}
