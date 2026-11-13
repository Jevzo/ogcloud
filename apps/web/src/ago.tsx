import { Tooltip } from "@ogcloud/ui";
import { useFormat } from "./format.ts";

/**
 * How long ago a moment was, or how soon it comes, in the interface's language: a `<time>` with the exact date and
 * time on hover (D-0231); a dash when there is none.
 */
export function Ago({
  at,
  className = "text-text-2",
}: {
  /** An ISO timestamp or epoch milliseconds. */
  at: string | number | null | undefined;
  /** The text's colour; sentences pass their own. */
  className?: string;
}) {
  const format = useFormat();
  const date = at == null ? null : new Date(at);
  if (date === null || Number.isNaN(date.getTime())) {
    return (
      <span data-skeleton="" className={className}>
        —
      </span>
    );
  }
  return (
    <Tooltip content={format.dateTime(date)}>
      <time
        data-skeleton=""
        dateTime={date.toISOString()}
        className={className}
      >
        {format.ago(date, Date.now())}
      </time>
    </Tooltip>
  );
}
