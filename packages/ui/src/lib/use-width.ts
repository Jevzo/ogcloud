import { type RefObject, useEffect, useState } from "react";

/** The element's current content width, following resizes; charts draw to it. */
export function useWidth(element: RefObject<HTMLElement | null>): number {
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const target = element.current;
    if (target === null) return;
    const observer = new ResizeObserver(([entry]) =>
      setWidth(entry?.contentRect.width ?? 0),
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [element]);
  return width;
}
