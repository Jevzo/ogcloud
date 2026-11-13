import { useRef } from "react";

/**
 * The last value that was not null: a drawer keeps showing its record while it slides out after the record is
 * closed or gone (D-0231).
 */
export function useHeld<T>(value: T | null): T | null {
  const held = useRef(value);
  if (value !== null) held.current = value;
  return held.current;
}
