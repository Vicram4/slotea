import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Slotea",
  description: "Reserva tu cita en segundos.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es">
      <body className="min-h-dvh antialiased">{children}</body>
    </html>
  );
}
