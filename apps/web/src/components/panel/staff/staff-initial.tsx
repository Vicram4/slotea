import { cn } from "@/lib/utils";

/** La inicial de un profesional en un círculo gris, como en las columnas de la agenda. */
export function StaffInitial({ name, muted, className }: { name: string; muted?: boolean; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        "bg-muted grid size-9 shrink-0 place-items-center rounded-full text-sm font-medium",
        muted ? "text-muted-foreground" : "text-foreground",
        className,
      )}
    >
      {name.trim().charAt(0).toUpperCase()}
    </span>
  );
}
