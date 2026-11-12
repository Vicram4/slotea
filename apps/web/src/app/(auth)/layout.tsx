import Link from "next/link";

export default function AuthLayout({ children }: LayoutProps<"/">) {
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center gap-8 px-6 py-12">
      <Link href="/" className="text-2xl font-bold tracking-tight">
        Slotea
      </Link>
      {children}
    </main>
  );
}
