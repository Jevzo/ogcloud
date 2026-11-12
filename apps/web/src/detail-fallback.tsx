import { EmptyState } from "@ogcloud/ui";
import type { LucideIcon } from "lucide-react";
import type { ReactNode } from "react";
import { messageOf } from "./errors.ts";
import { HttpStatus, statusOf } from "./http.ts";

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

/** Why a detail page's subject is missing or failed to load, with the way back; while it loads, the page shows itself. */
export function DetailFallback({
  error,
  missing = false,
  icon,
  goneTitle,
  goneDescription,
  failedTitle,
  back,
}: DetailFallbackProps) {
  const gone = missing || statusOf(error) === HttpStatus.NOT_FOUND;
  return (
    <EmptyState
      icon={icon}
      title={gone ? goneTitle : failedTitle}
      description={gone ? goneDescription : messageOf(error)}
      action={back}
    />
  );
}
