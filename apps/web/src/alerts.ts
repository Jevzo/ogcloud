import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from "react";
import type { Alert } from "./live.ts";

/** An alert from the event stream and when it arrived; alerts are not stored by the API (D-0105). */
export interface ReceivedAlert extends Alert {
  at: number;
}

export interface Alerts {
  alerts: readonly ReceivedAlert[];
  unseen: number;
  add: (alert: Alert) => void;
  markSeen: () => void;
}

// Alerts live only while the dashboard is open; the newest are kept.
const KEPT = 100;

export const AlertsContext = createContext<Alerts>({
  alerts: [],
  unseen: 0,
  add: () => undefined,
  markSeen: () => undefined,
});

export function useAlerts(): Alerts {
  return useContext(AlertsContext);
}

/** The shell's alert list: newest first, with how many arrived since Activity last showed them. */
export function useAlertList(now: () => number): Alerts {
  // `received` counts every alert ever added, so the badge keeps counting once the list is full.
  const [list, setList] = useState<{
    alerts: readonly ReceivedAlert[];
    received: number;
  }>({ alerts: [], received: 0 });
  const [seen, setSeen] = useState(0);
  const add = useCallback(
    (alert: Alert) =>
      setList((current) => ({
        alerts: [{ ...alert, at: now() }, ...current.alerts].slice(0, KEPT),
        received: current.received + 1,
      })),
    [now],
  );
  const markSeen = useCallback(() => setSeen(list.received), [list.received]);
  return useMemo(
    () => ({
      alerts: list.alerts,
      unseen: list.received - seen,
      add,
      markSeen,
    }),
    [list, seen, add, markSeen],
  );
}
