import { ApiError } from "@ogcloud/api-client";
import { EmptyState, Skeleton } from "@ogcloud/ui";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

const NOT_FOUND = 404;

interface DetailFallbackProps {
  error: unknown;
  /** The subject is known not to exist, such as when it is missing from a list that loaded. */
  missing?: boolean;
  icon: LucideIcon;
  goneTitle: string;
  goneDescription: string;
  failedTitle: string;
  /** The way back to the list, such as a link. */
  back: ReactNode;
}

/** A detail page while it loads (no error yet), or why its subject is missing, with the way back. */
export function DetailFallback({
  error,
  missing = false,
  icon,
  goneTitle,
  goneDescription,
  failedTitle,
  back,
}: DetailFallbackProps) {
  if (!missing && (error === null || error === undefined)) {
    return (
      <div className="flex flex-col gap-4" aria-busy="true">
        <Skeleton className="h-10 w-72" />
        <Skeleton className="h-80 max-w-form rounded-card" />
      </div>
    );
  }
  const gone =
    missing || (error instanceof ApiError && error.status === NOT_FOUND);
  return (
    <EmptyState
      icon={icon}
      title={gone ? goneTitle : failedTitle}
      description={
        gone ? goneDescription : error instanceof Error ? error.message : ""
      }
      action={back}
    />
  );
}
