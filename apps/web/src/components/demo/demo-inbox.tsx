"use client";

import { Inbox } from "lucide-react";
import { useState } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { ApiError } from "@/lib/api/client";
import { type InboxEmail, useDemoEmail, useDemoInbox } from "@/lib/demo";
import { useMe } from "@/lib/session";
import { cn } from "@/lib/utils";

const SENT_AT = new Intl.DateTimeFormat("es-ES", { dateStyle: "short", timeStyle: "short" });

/** Los enlaces del email se abren en otra pestaña (el iframe no puede navegar por su cuenta). */
export function emailDocument(html: string): string {
  const base = '<base target="_blank">';
  return /<head[^>]*>/i.test(html) ? html.replace(/<head[^>]*>/i, (head) => head + base) : base + html;
}

/**
 * Bandeja de la demo: los emails que habría mandado esta copia (confirmaciones al cliente y avisos al
 * negocio), tal y como se verían. En la demo no se envía ninguno de verdad.
 */
export function DemoInbox() {
  const me = useMe();
  const isDemoOwner = me.data?.activeBusiness?.demo === true && me.data.activeBusiness.role === "OWNER";

  return (
    <>
      <header>
        <h1 className="text-2xl font-semibold">Bandeja de la demo</h1>
        <p className="text-muted-foreground text-sm">
          En la demo no se envía ningún email: aquí ves los que habrían recibido el cliente y el negocio. Reserva,
          cambia o cancela una cita como cliente y vuelve aquí.
        </p>
      </header>
      {isDemoOwner ? (
        <InboxContent />
      ) : (
        <Alert>
          <AlertDescription>La bandeja solo existe en la demo, para el dueño del negocio.</AlertDescription>
        </Alert>
      )}
    </>
  );
}

function InboxContent() {
  const inbox = useDemoInbox();
  const [selected, setSelected] = useState<string | null>(null);

  if (inbox.isPending) return <p role="status">Cargando la bandeja…</p>;
  if (inbox.isError) {
    const gone = inbox.error instanceof ApiError && inbox.error.status === 404;
    return (
      <Alert variant="destructive">
        <AlertDescription>
          {gone ? "La bandeja solo existe en la demo." : "No se pudo cargar la bandeja. Recarga la página."}
        </AlertDescription>
      </Alert>
    );
  }
  if (inbox.data.length === 0) {
    return (
      <div className="text-muted-foreground grid justify-items-center gap-2 rounded-lg border border-dashed p-10 text-center">
        <Inbox className="size-8" aria-hidden />
        <p>Todavía no hay emails.</p>
        <p className="text-sm">Haz una reserva como cliente (o cancela una cita desde la agenda) y aparecerá aquí.</p>
      </div>
    );
  }

  const current = selected ?? inbox.data[0]!.id;
  return (
    <div className="grid gap-4 lg:grid-cols-[18rem_1fr]">
      <ul aria-label="Emails" className="grid content-start gap-2">
        {inbox.data.map((email) => (
          <li key={email.id}>
            <EmailRow email={email} current={email.id === current} onSelect={() => setSelected(email.id)} />
          </li>
        ))}
      </ul>
      <EmailPreview id={current} />
    </div>
  );
}

function EmailRow({ email, current, onSelect }: { email: InboxEmail; current: boolean; onSelect: () => void }) {
  return (
    <button
      type="button"
      onClick={onSelect}
      aria-pressed={current}
      className={cn(
        "hover:bg-accent grid w-full gap-1 rounded-lg border p-3 text-left text-sm",
        current && "border-primary ring-primary/30 ring-2",
      )}
    >
      <span className="flex items-center justify-between gap-2 text-xs">
        <span
          className={cn(
            "rounded px-1.5 py-0.5 font-medium",
            email.toBusiness ? "bg-sky-100 text-sky-900" : "bg-emerald-100 text-emerald-900",
          )}
        >
          {email.toBusiness ? "Al negocio" : "Al cliente"}
        </span>
        <span className="text-muted-foreground">{SENT_AT.format(new Date(email.createdAt))}</span>
      </span>
      <span className="font-medium">{email.subject}</span>
      <span className="text-muted-foreground truncate text-xs">Para: {email.to}</span>
    </button>
  );
}

function EmailPreview({ id }: { id: string }) {
  const email = useDemoEmail(id);
  if (email.isPending) return <p role="status">Cargando el email…</p>;
  if (email.isError) {
    return (
      <Alert variant="destructive">
        <AlertDescription>No se pudo abrir este email.</AlertDescription>
      </Alert>
    );
  }
  const { subject, to, html } = email.data;
  return (
    <article aria-label={subject} className="grid content-start gap-2 rounded-lg border p-4">
      <h2 className="font-semibold">{subject}</h2>
      <p className="text-muted-foreground text-sm">Para: {to}</p>
      {/* Sin allow-scripts ni allow-same-origin: el HTML del email no puede ejecutar nada ni leer la sesión. */}
      <iframe
        title={`Contenido del email: ${subject}`}
        srcDoc={emailDocument(html)}
        sandbox="allow-popups allow-popups-to-escape-sandbox"
        className="h-[36rem] w-full rounded border bg-white"
      />
    </article>
  );
}
