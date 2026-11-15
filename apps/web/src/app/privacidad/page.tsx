import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";

export const metadata: Metadata = { title: "Privacidad · Slotea" };

const REPO = "https://github.com/Vicram4/slotea";

/** Qué datos guarda la app y por qué. Slotea es una demo: este texto lo dice tal cual, sin letra pequeña. */
export default function PrivacyPage() {
  return (
    <main className="mx-auto grid max-w-2xl gap-10 px-6 py-12 md:py-20">
      <header className="grid gap-3">
        <Link href="/" className="flex w-fit items-center gap-2 text-sm font-semibold tracking-tight">
          <span
            aria-hidden
            className="bg-foreground text-background grid size-6 place-items-center rounded-md text-xs font-semibold"
          >
            S
          </span>
          Slotea
        </Link>
        <h1 className="text-3xl font-semibold tracking-tight">Privacidad</h1>
        <p className="text-muted-foreground text-pretty">
          Slotea es un <strong className="text-foreground font-medium">proyecto de demostración</strong> (un
          portfolio), no un servicio comercial. Pruébalo con la demo; no lo uses para un negocio real ni con datos
          reales.
        </p>
      </header>

      <Section title="Qué se guarda">
        <ul className="grid list-disc gap-1.5 pl-5">
          <li>
            Si registras un negocio: tu nombre, tu email y un hash irreversible de tu contraseña (nunca la contraseña
            en sí).
          </li>
          <li>Los datos del negocio: nombre, email de contacto, equipo, servicios, horarios y cierres.</li>
          <li>De cada reserva: el nombre y el email del cliente y, si los escribe, su teléfono y sus comentarios.</li>
          <li>Los emails de confirmación y de aviso que genera cada reserva, cambio o cancelación.</li>
        </ul>
      </Section>

      <Section title="Para qué">
        Solo para que la aplicación funcione: enseñar la agenda, confirmar las citas y dejar cambiarlas o
        cancelarlas. No hay analítica, publicidad ni cookies de seguimiento. Las únicas cookies son las de la sesión y
        la de protección contra CSRF, sin las que no se puede entrar en el panel.
      </Section>

      <Section title="Dónde">
        En los servicios que hacen funcionar la aplicación: Vercel (la web), Google Cloud en Bélgica (la API), Neon en
        Frankfurt (la base de datos) y Brevo (el envío de emails). Esos proveedores guardan además registros técnicos
        durante un tiempo limitado, como la dirección IP de cada petición. Los datos no se venden ni se ceden a nadie.
      </Section>

      <Section title="Cuánto tiempo">
        Las copias de la demo se borran a las 24 horas, con todo lo que tengan. Los negocios registrados se guardan
        hasta que se pida borrarlos.
      </Section>

      <Section title="Borrar tus datos">
        Abre una{" "}
        <a href={`${REPO}/issues`} className="text-foreground underline underline-offset-4">
          incidencia en GitHub
        </a>{" "}
        con la dirección de tu página de reservas. No escribas en ella tu email ni otros datos personales: la
        incidencia es pública.
      </Section>
    </main>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid gap-2">
      <h2 className="font-medium">{title}</h2>
      <div className="text-muted-foreground text-sm leading-relaxed text-pretty">{children}</div>
    </section>
  );
}
