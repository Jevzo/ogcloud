import { cn } from "@ogcloud/ui";
import { Check } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { BrandedPage } from "../../branded-page.tsx";

/** A fresh install's setup, in the order it happens: before signing in, then as the new owner. */
export const SETUP_STEPS = [
  "token",
  "owner",
  "signIn",
  "eula",
  "groups",
] as const;
export type SetupStep = (typeof SETUP_STEPS)[number];

/** The setup's frame: where it stands on the left, the current step on the right. */
export function SetupFrame({
  step,
  children,
}: {
  step: SetupStep;
  children: ReactNode;
}) {
  const { t } = useTranslation("web");
  const current = SETUP_STEPS.indexOf(step);
  return (
    <BrandedPage className="grid max-w-[50rem] grid-cols-[12rem_minmax(0,1fr)] gap-8">
      <ol aria-label={t("setup.progress")} className="flex flex-col gap-1">
        {SETUP_STEPS.map((name, index) => (
          <li
            key={name}
            aria-current={index === current ? "step" : undefined}
            className={cn(
              "flex items-center gap-2.5 rounded-control px-2 py-1.5",
              index === current
                ? "bg-accent-soft font-label text-accent-text"
                : "text-text-3",
              index < current && "text-text-2",
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                "grid size-5.5 shrink-0 place-items-center rounded-full border text-meta tabular-nums",
                index === current
                  ? "border-accent bg-accent text-on-accent"
                  : index < current
                    ? "border-success bg-success text-on-accent"
                    : "border-border",
              )}
            >
              {index < current ? (
                <Check size={12} strokeWidth={3} />
              ) : (
                index + 1
              )}
            </span>
            {t(`setup.steps.${name}`)}
          </li>
        ))}
      </ol>
      <section className="flex min-w-0 flex-col gap-4">{children}</section>
    </BrandedPage>
  );
}

/** A step's title and what it is for. */
export function StepHeading({
  title,
  description,
}: {
  title: string;
  description: ReactNode;
}) {
  return (
    <div>
      <h1 className="text-title">{title}</h1>
      <p className="mt-1 text-text-2">{description}</p>
    </div>
  );
}

/** A step's actions at its end, the one that moves on last (D-0210). */
export function StepActions({ children }: { children: ReactNode }) {
  return <div className="mt-2 flex justify-end gap-2">{children}</div>;
}
