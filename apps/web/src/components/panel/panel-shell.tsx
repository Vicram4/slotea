"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense, useEffect, type ReactNode } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { roleLabels, useLogout, useMe } from "@/lib/session";
import { cn } from "@/lib/utils";

export const PANEL_LINKS = [
  { href: "/panel", label: "Inicio" },
  { href: "/panel/servicios", label: "Servicios" },
  { href: "/panel/profesionales", label: "Profesionales" },
  { href: "/panel/cierres", label: "Cierres" },
] as const;

function isCurrent(pathname: string | null, href: string): boolean {
  if (pathname === null) return false;
  return href === "/panel" ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
}

function NavLinks({ pathname }: { pathname: string | null }) {
  return (
    <nav aria-label="Panel">
      <ul className="flex flex-wrap gap-1 md:flex-col">
        {PANEL_LINKS.map((link) => {
          const current = isCurrent(pathname, link.href);
          return (
            <li key={link.href}>
              <Link
                href={link.href}
                aria-current={current ? "page" : undefined}
                className={cn("hover:bg-accent block rounded-md px-3 py-2 text-sm", current && "bg-accent font-medium")}
              >
                {link.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

/**
 * `usePathname` en una ruta con parámetros (/panel/profesionales/[id]) no se conoce al prerenderizar:
 * va dentro de un Suspense cuyo fallback es la misma navegación sin marcar la página actual.
 */
function PanelNav() {
  return (
    <Suspense fallback={<NavLinks pathname={null} />}>
      <CurrentNav />
    </Suspense>
  );
}

function CurrentNav() {
  return <NavLinks pathname={usePathname()} />;
}

/**
 * Marco común del panel: comprueba la sesión (sin sesión, al login), muestra el negocio y la
 * navegación, y solo entonces pinta la página. Los datos dependen de cookies httpOnly, así que todo
 * se carga en el cliente.
 */
export function PanelShell({ children }: { children: ReactNode }) {
  const router = useRouter();
  const me = useMe();
  const logout = useLogout();

  useEffect(() => {
    if (me.data === null) router.replace("/login");
  }, [me.data, router]);

  if (me.isPending || me.data === null) {
    return (
      <p role="status" className="p-12 text-center">
        Cargando tu panel…
      </p>
    );
  }
  if (me.isError) {
    return (
      <Alert variant="destructive" className="m-12 max-w-xl">
        <AlertDescription>No se pudo cargar tu cuenta. Recarga la página.</AlertDescription>
      </Alert>
    );
  }

  const { user, activeBusiness } = me.data;

  const onLogout = async () => {
    await logout.mutateAsync().catch(() => undefined);
    router.replace("/login");
  };

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[14rem_1fr]">
      <aside className="bg-muted/40 flex flex-col gap-6 border-b p-4 md:border-r md:border-b-0">
        <div className="grid gap-1">
          <span className="text-lg font-semibold">{activeBusiness?.name ?? "Slotea"}</span>
          <span className="text-muted-foreground text-sm">
            {user.fullName}
            {activeBusiness && ` · ${roleLabels[activeBusiness.role]}`}
          </span>
        </div>
        {activeBusiness && <PanelNav />}
        <Button variant="outline" size="sm" className="md:mt-auto" onClick={onLogout} disabled={logout.isPending}>
          {logout.isPending ? "Cerrando sesión…" : "Cerrar sesión"}
        </Button>
      </aside>
      <main className="mx-auto grid w-full max-w-4xl content-start gap-6 p-6 md:p-10">
        {activeBusiness ? children : <p>Tu cuenta todavía no pertenece a ningún negocio.</p>}
      </main>
    </div>
  );
}
