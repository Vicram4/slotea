import Link from "next/link";

import { DemoLauncher } from "@/components/demo/demo-launcher";
import { Button } from "@/components/ui/button";
import { WakeUpApi } from "@/components/wake-up-api";

/** Lo que hace distinto al producto, en una frase cada cosa y sin adornos. */
const POINTS = [
  {
    title: "Huecos que encajan",
    text: "Cada servicio dura lo que dura: un tinte de 2 h solo se ofrece donde caben 2 h, con el margen para recoger incluido.",
  },
  {
    title: "Nunca dos en el mismo hueco",
    text: "Si dos clientes pulsan la misma hora a la vez, uno se la queda y el otro elige otra. Lo garantiza la base de datos, no la suerte.",
  },
  {
    title: "Cada uno, lo suyo",
    text: "El dueño ve todo el negocio; cada empleado, su agenda y sus ausencias. El cliente, solo un enlace para cambiar o cancelar.",
  },
] as const;

export default function Home() {
  return (
    <div className="mx-auto flex min-h-dvh max-w-5xl flex-col px-6">
      <WakeUpApi />
      <header className="flex items-center justify-between gap-4 py-5">
        <Link href="/" className="flex items-center gap-2 text-lg font-semibold tracking-tight">
          <span
            aria-hidden
            className="bg-foreground text-background grid size-7 place-items-center rounded-md text-sm font-semibold"
          >
            S
          </span>
          Slotea
        </Link>
        <nav aria-label="Cuenta" className="flex items-center gap-1">
          <Button asChild variant="ghost" size="sm">
            <Link href="/login">Entrar</Link>
          </Button>
          <Button asChild size="sm">
            <Link href="/registro">Registra tu negocio</Link>
          </Button>
        </nav>
      </header>

      <main className="grid flex-1 content-start gap-14 py-12 md:py-20">
        <section className="grid max-w-2xl gap-4">
          <h1 className="text-4xl font-semibold tracking-tight text-balance md:text-5xl">
            Que te reserven sin tener que llamarte.
          </h1>
          <p className="text-muted-foreground text-lg text-pretty">
            Una página de reservas con el color de tu negocio, una agenda para ti y otra para cada persona del equipo, y
            un email con enlace para cambiar o cancelar la cita.
          </p>
        </section>

        <DemoLauncher />

        <section aria-label="Cómo funciona" className="grid gap-8 border-t pt-10 md:grid-cols-3">
          {POINTS.map(({ title, text }) => (
            <div key={title} className="grid content-start gap-1.5">
              <h2 className="text-sm font-medium">{title}</h2>
              <p className="text-muted-foreground text-sm text-pretty">{text}</p>
            </div>
          ))}
        </section>
      </main>

      <footer className="text-muted-foreground flex flex-wrap justify-between gap-x-6 gap-y-2 border-t py-6 text-xs">
        <span>Slotea es un proyecto de portfolio. Los negocios de la demo y sus clientes son ficticios.</span>
        <span className="flex gap-4">
          <Link href="/privacidad" className="hover:text-foreground underline-offset-4 hover:underline">
            Privacidad
          </Link>
          <a
            href="https://github.com/Vicram4/slotea"
            className="hover:text-foreground underline-offset-4 hover:underline"
            target="_blank"
            rel="noreferrer"
          >
            Código en GitHub
          </a>
        </span>
      </footer>
    </div>
  );
}
