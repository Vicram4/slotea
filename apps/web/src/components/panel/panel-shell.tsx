"use client";

import {
  CalendarDays,
  CalendarOff,
  ExternalLink,
  House,
  Inbox,
  LogOut,
  Settings,
  Tags,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { Suspense, useEffect, type ReactNode } from "react";

import { DemoBar } from "@/components/demo/demo-bar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useBusiness } from "@/lib/agenda";
import { useBrandColor } from "@/lib/brand";
import { roleLabels, useLogout, useMe } from "@/lib/session";
import { cn } from "@/lib/utils";

type NavLink = { href: string; label: string; icon: LucideIcon };

export const PANEL_LINKS: readonly NavLink[] = [
  { href: "/panel", label: "Inicio", icon: House },
  { href: "/panel/agenda", label: "Agenda", icon: CalendarDays },
  { href: "/panel/servicios", label: "Servicios", icon: Tags },
  { href: "/panel/profesionales", label: "Profesionales", icon: Users },
  { href: "/panel/cierres", label: "Cierres", icon: CalendarOff },
];

/** Solo para el dueño: el empleado no puede cambiar los datos del negocio (la API da 403). */
export const SETTINGS_LINK: NavLink = { href: "/panel/ajustes", label: "Ajustes", icon: Settings };

/** Solo en una copia de la demo y para el dueño (los emails van al negocio y a los clientes). */
export const DEMO_INBOX_LINK: NavLink = { href: "/panel/bandeja", label: "Bandeja de la demo", icon: Inbox };

function isCurrent(pathname: string | null, href: string): boolean {
  if (pathname === null) return false;
  return href === "/panel" ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);
}

function NavLinks({ pathname, links }: { pathname: string | null; links: readonly NavLink[] }) {
  return (
    <nav aria-label="Panel">
      <ul className="flex flex-wrap gap-0.5 md:flex-col">
        {links.map(({ href, label, icon: Icon }) => {
          const current = isCurrent(pathname, href);
          return (
            <li key={href}>
              <Link
                href={href}
                aria-current={current ? "page" : undefined}
                className={cn(
                  "flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-sm transition-colors",
                  current
                    ? "bg-background text-foreground font-medium shadow-xs ring-1 ring-black/5"
                    : "text-muted-foreground hover:bg-background/70 hover:text-foreground",
                )}
              >
                <Icon className="size-4 shrink-0" aria-hidden />
                {label}
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
function PanelNav({ links }: { links: readonly NavLink[] }) {
  return (
    <Suspense fallback={<NavLinks pathname={null} links={links} />}>
      <CurrentNav links={links} />
    </Suspense>
  );
}

function CurrentNav({ links }: { links: readonly NavLink[] }) {
  return <NavLinks pathname={usePathname()} links={links} />;
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
      <p role="status" className="text-muted-foreground p-12 text-center text-sm">
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
  const isOwner = activeBusiness?.role === "OWNER";
  const links: readonly NavLink[] = [
    ...PANEL_LINKS,
    ...(isOwner ? [SETTINGS_LINK] : []),
    ...(isOwner && activeBusiness.demo ? [DEMO_INBOX_LINK] : []),
  ];

  const onLogout = async () => {
    await logout.mutateAsync().catch(() => undefined);
    router.replace("/login");
  };

  return (
    <div className="min-h-dvh md:grid md:grid-cols-[15rem_1fr] md:grid-rows-[auto_1fr]">
      <DemoBar me={me.data} />
      <aside className="bg-muted/50 flex flex-col gap-6 border-b p-4 md:sticky md:top-0 md:h-dvh md:border-r md:border-b-0">
        <div className="flex items-center gap-3 px-1">
          {activeBusiness && <BrandMark name={activeBusiness.name} />}
          <div className="grid min-w-0">
            <span className="truncate text-sm font-semibold">{activeBusiness?.name ?? "Slotea"}</span>
            {activeBusiness && (
              <span className="text-muted-foreground text-xs">{roleLabels[activeBusiness.role]}</span>
            )}
          </div>
        </div>
        {activeBusiness && <PanelNav links={links} />}
        <div className="grid gap-1 md:mt-auto">
          {activeBusiness && (
            <Link
              href={`/${activeBusiness.slug}`}
              className="text-muted-foreground hover:text-foreground flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-sm"
            >
              <ExternalLink className="size-4 shrink-0" aria-hidden />
              Tu página de reservas
            </Link>
          )}
          <div className="flex items-center justify-between gap-2 border-t pt-3 pl-2.5">
            <span className="text-muted-foreground truncate text-xs">{user.fullName}</span>
            <Button variant="ghost" size="sm" onClick={onLogout} disabled={logout.isPending}>
              <LogOut aria-hidden />
              {logout.isPending ? "Cerrando sesión…" : "Cerrar sesión"}
            </Button>
          </div>
        </div>
      </aside>
      <main className="mx-auto grid w-full max-w-6xl content-start gap-6 p-6 md:p-10">
        {activeBusiness ? children : <p>Tu cuenta todavía no pertenece a ningún negocio.</p>}
      </main>
    </div>
  );
}

/**
 * La inicial del negocio en su color, como en la página pública. Pide los datos del negocio (que la
 * agenda también usa: van a la misma caché) y pinta todo el panel con su color de marca.
 */
function BrandMark({ name }: { name: string }) {
  const business = useBusiness();
  useBrandColor(business.data?.brandColor);
  return (
    <span
      aria-hidden
      className="bg-primary text-primary-foreground grid size-8 shrink-0 place-items-center rounded-md text-sm font-semibold"
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
}
