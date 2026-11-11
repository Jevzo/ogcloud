import { Card, cn } from "@ogcloud/ui";
import type { ReactNode } from "react";
import mascot from "./assets/mascot-256.png";

/** A page outside the dashboard's shell, such as the sign-in or the first setup: the mascot, the wordmark, one card. */
export function BrandedPage({
  className,
  children,
}: {
  /** Sizes the card; the sign-in's is narrow. */
  className?: string;
  children: ReactNode;
}) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <div className="flex items-center gap-3">
        <img src={mascot} alt="" className="size-14" />
        <span className="font-heavy text-[26px] tracking-tight">OgCloud</span>
      </div>
      <Card className={cn("w-full max-w-[26rem] p-6", className)}>
        {children}
      </Card>
    </main>
  );
}
