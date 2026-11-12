import { PanelShell } from "@/components/panel/panel-shell";

export default function PanelLayout({ children }: LayoutProps<"/panel">) {
  return <PanelShell>{children}</PanelShell>;
}
