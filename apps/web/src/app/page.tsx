import { ApiStatus } from "@/components/api-status";

export default function Home() {
  return (
    <main className="mx-auto flex max-w-xl flex-col gap-6 px-6 py-16">
      <h1 className="text-4xl font-bold tracking-tight">Slotea</h1>
      <p className="text-lg text-neutral-600">Reserva tu cita en segundos.</p>
      <ApiStatus />
    </main>
  );
}
