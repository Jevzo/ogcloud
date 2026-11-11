import { useEffect, useState } from "react";

/** How long typing pauses before a search or a check goes out. */
export const TYPING_PAUSE_MS = 300;

/** The value once it has stopped changing for [delay] ms, e.g. while someone types. */
export function useDebounced<T>(value: T, delay: number): T {
  const [settled, setSettled] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return settled;
}
