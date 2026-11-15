import Link from "next/link";

import { ApiStatus } from "@/components/api-status";
import { DemoLauncher } from "@/components/demo/demo-launcher";
import { Button } from "@/components/ui/button";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-3xl flex-col gap-8 px-6 py-16">
      <header className="grid gap-3">
        <h1 className="text-4xl font-bold tracking-tight">Slotea</h1>
        <p className="text-muted-foreground text-lg">
          Reservas de citas para barberías, peluquerías y centros de estética: página pública de reservas, agenda
          del negocio y de cada empleado, y emails de confirmación con enlace para cambiar o cancelar.
        </p>
      </header>
      <DemoLauncher />
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-muted-foreground text-sm">¿Tienes un negocio?</span>
        <Button asChild variant="outline">
          <Link href="/registro">Regístralo</Link>
        </Button>
        <Button asChild variant="ghost">
          <Link href="/login">Entrar</Link>
        </Button>
      </div>
      <ApiStatus />
    </main>
  );
}
