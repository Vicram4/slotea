import Link from "next/link";

export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-sm flex-col justify-center gap-10 px-6 py-12">
      <Link href="/" className="flex w-fit items-center gap-2 text-lg font-semibold tracking-tight">
        <span
          aria-hidden
          className="bg-foreground text-background grid size-7 place-items-center rounded-md text-sm font-semibold"
        >
          S
        </span>
        Slotea
      </Link>
      {children}
      {/* Quien llega aquí solo para mirar (p. ej. desde el README) no necesita una cuenta. */}
      <p className="text-muted-foreground border-t pt-6 text-sm">
        ¿Solo quieres echar un vistazo?{" "}
        <Link href="/" className="text-foreground underline underline-offset-4">
          Prueba la demo sin registrarte
        </Link>
        .
      </p>
    </main>
  );
}
