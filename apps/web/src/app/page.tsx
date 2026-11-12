import Link from "next/link";

import { ApiStatus } from "@/components/api-status";
import { Button } from "@/components/ui/button";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-xl flex-col gap-6 px-6 py-16">
      <h1 className="text-4xl font-bold tracking-tight">Slotea</h1>
      <p className="text-muted-foreground text-lg">Reserva tu cita en segundos.</p>
      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link href="/registro">Registra tu negocio</Link>
        </Button>
        <Button asChild variant="outline">
          <Link href="/login">Entrar</Link>
        </Button>
      </div>
      <ApiStatus />
    </main>
  );
}
