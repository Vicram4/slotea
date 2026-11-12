"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { roleLabels, useLogout, useMe } from "@/lib/session";

/** «Quién soy»: usuario, negocio activo y rol. Sin sesión, manda al login. */
export function PanelHome() {
  const router = useRouter();
  const me = useMe();
  const logout = useLogout();

  useEffect(() => {
    if (me.data === null) router.replace("/login");
  }, [me.data, router]);

  if (me.isPending || me.data === null) {
    return <p role="status">Cargando tu panel…</p>;
  }
  if (me.isError) {
    return (
      <Alert variant="destructive">
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
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Hola, {user.fullName}</CardTitle>
        <CardDescription>{user.email}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        {activeBusiness ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Negocio</dt>
            <dd className="font-medium">{activeBusiness.name}</dd>
            <dt className="text-muted-foreground">Tu página</dt>
            <dd className="font-medium">/{activeBusiness.slug}</dd>
            <dt className="text-muted-foreground">Tu rol</dt>
            <dd className="font-medium">{roleLabels[activeBusiness.role]}</dd>
          </dl>
        ) : (
          <p className="text-sm">Tu cuenta todavía no pertenece a ningún negocio.</p>
        )}
        <Button variant="outline" className="justify-self-start" onClick={onLogout} disabled={logout.isPending}>
          {logout.isPending ? "Cerrando sesión…" : "Cerrar sesión"}
        </Button>
      </CardContent>
    </Card>
  );
}
