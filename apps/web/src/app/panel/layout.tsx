import { PanelShell } from "@/components/panel/panel-shell";

/**
 * El panel depende de la sesión (cookies httpOnly) y pinta cada página solo cuando la tiene. Por eso
 * cada página del panel declara `instant = false`: si no, Next avisa en desarrollo de que no se
 * renderiza al validar la navegación instantánea (`instant-unrendered-segment`).
 */
export default function PanelLayout({ children }: LayoutProps<"/panel">) {
  return <PanelShell>{children}</PanelShell>;
}
