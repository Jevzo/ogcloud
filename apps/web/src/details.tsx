import type { ReactNode } from "react";

/** Labelled facts about one thing, label beside value. */
export function Details({ children }: { children: ReactNode }) {
  return (
    <dl className="grid grid-cols-[max-content_minmax(0,1fr)] items-baseline gap-x-8 gap-y-3">
      {children}
    </dl>
  );
}

export function DetailRow({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <>
      <dt className="text-text-3">{label}</dt>
      <dd className="m-0">{children}</dd>
    </>
  );
}
