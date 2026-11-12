"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type ChangeEvent } from "react";
import { useForm, useWatch } from "react-hook-form";

import { FormField } from "@/components/forms/form-field";
import { applyServerErrors } from "@/components/forms/server-errors";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { useSignup } from "@/lib/session";
import { signupSchema, slugify, type SignupValues } from "@/lib/validation/auth";

const FIELDS = ["businessName", "slug", "fullName", "email", "password"] as const;

/** Zona del navegador si es de región (Europe/Madrid); si no (UTC), la API usa Europe/Madrid. */
function browserTimeZone(): string | undefined {
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return zone.includes("/") ? zone : undefined;
}

export function SignupForm() {
  const router = useRouter();
  const signup = useSignup();
  const [formError, setFormError] = useState<string | null>(null);
  // Mientras el usuario no toque la dirección, se propone a partir del nombre del negocio.
  const [slugEdited, setSlugEdited] = useState(false);
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { businessName: "", slug: "", fullName: "", email: "", password: "" },
  });
  const slug = useWatch({ control, name: "slug" });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await signup.mutateAsync({ ...values, timezone: browserTimeZone() });
      router.push("/panel");
    } catch (error) {
      setFormError(applyServerErrors(error, FIELDS, setError));
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <FormField
        label="Nombre del negocio"
        autoComplete="organization"
        error={errors.businessName?.message}
        {...register("businessName", {
          onChange: (event: ChangeEvent<HTMLInputElement>) => {
            if (!slugEdited) setValue("slug", slugify(event.target.value));
          },
        })}
      />
      <FormField
        label="Dirección de tu página de reservas"
        autoCapitalize="none"
        spellCheck={false}
        error={errors.slug?.message}
        hint={`Tu página será /${slug || "tu-negocio"}`}
        {...register("slug", { onChange: () => setSlugEdited(true) })}
      />
      <FormField label="Tu nombre" autoComplete="name" error={errors.fullName?.message} {...register("fullName")} />
      <FormField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register("email")} />
      <FormField
        label="Contraseña"
        type="password"
        autoComplete="new-password"
        hint="Mínimo 10 caracteres."
        error={errors.password?.message}
        {...register("password")}
      />
      <Button type="submit" disabled={isSubmitting}>
        {isSubmitting ? "Creando tu cuenta…" : "Crear cuenta"}
      </Button>
      <p className="text-muted-foreground text-center text-sm">
        ¿Ya tienes cuenta?{" "}
        <Link href="/login" className="text-foreground underline underline-offset-4">
          Entra
        </Link>
      </p>
    </form>
  );
}
