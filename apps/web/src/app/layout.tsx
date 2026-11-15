import type { Metadata } from "next";
import { Inter } from "next/font/google";

import "./globals.css";
import { Providers } from "./providers";

/** Una sola familia para toda la app. `next/font` la sirve desde nuestro dominio (sin peticiones a Google). */
const inter = Inter({ subsets: ["latin"], variable: "--font-inter", display: "swap" });

export const metadata: Metadata = {
  title: "Slotea",
  description: "Reserva tu cita en segundos.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es" className={inter.variable}>
      <body className="min-h-dvh antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
