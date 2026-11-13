import { useEffect, useState } from "react";

// The band a section must reach to count as the one being read: below the 56 px top bar, down to mid-window.
const READING_BAND = "-72px 0px -50% 0px";

/** Which of a page's sections the reader is at: the first, in page order, that reaches the window's upper half. */
export function useSectionInView(ids: readonly string[]): string | null {
  const [current, setCurrent] = useState<string | null>(null);
  useEffect(() => {
    const reached = new Set<string>();
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) reached.add(entry.target.id);
          else reached.delete(entry.target.id);
        }
        setCurrent(ids.find((id) => reached.has(id)) ?? null);
      },
      { rootMargin: READING_BAND },
    );
    for (const id of ids) {
      const section = document.getElementById(id);
      if (section !== null) observer.observe(section);
    }
    return () => observer.disconnect();
  }, [ids]);
  return current;
}
