import { useState } from "react";

/** Which action's dialog is open and for what; the target outlives the closing animation. */
export function usePendingAction<A extends string, T>() {
  const [state, setState] = useState<{
    action: A;
    target: T;
    open: boolean;
  } | null>(null);
  return {
    target: state?.target,
    isOpen: (action: A) => state?.open === true && state.action === action,
    open: (action: A, target: T) => setState({ action, target, open: true }),
    onOpenChange: (open: boolean) => {
      if (!open) setState((current) => current && { ...current, open: false });
    },
  };
}
