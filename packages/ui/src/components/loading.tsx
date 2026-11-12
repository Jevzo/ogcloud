import { createContext, type ReactNode, useContext } from "react";
import { cn } from "../lib/cn.ts";

const LoadingContext = createContext(false);

/** Whether a surrounding view is loading, for parts that draw differently then, such as tables. */
export function useLoading(): boolean {
  return useContext(LoadingContext);
}

/**
 * A view whose data is on its way (D-0228): it renders its real layout, usually from typed placeholder data, and every
 * part marked `data-skeleton` inside stands as a skeleton of its own size, while labels stay. Nothing in it reacts.
 */
export function Loading({
  when,
  children,
}: {
  when: boolean;
  children: ReactNode;
}) {
  const outer = useLoading();
  return (
    <LoadingContext.Provider value={outer || when}>
      <div
        className="contents"
        data-loading={when ? "" : undefined}
        aria-busy={when || undefined}
        inert={when}
      >
        {children}
      </div>
    </LoadingContext.Provider>
  );
}

/** Text that shows data, so it turns into a skeleton while its view loads. */
export function Value({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    // A box of its own, so values laid out as rows (a badge beside a time) still draw one skeleton.
    <span data-skeleton="" className={cn("inline-block max-w-full", className)}>
      {children}
    </span>
  );
}
