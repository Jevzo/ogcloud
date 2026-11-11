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
  const [alerts, setAlerts] = useState<readonly ReceivedAlert[]>([]);
  const [seen, setSeen] = useState(0);
  const add = useCallback(
    (alert: Alert) =>
      setAlerts((current) =>
        [{ ...alert, at: now() }, ...current].slice(0, KEPT),
      ),
    [now],
  );
  const markSeen = useCallback(() => setSeen(alerts.length), [alerts.length]);
  return useMemo(
    () => ({
      alerts,
      unseen: Math.max(0, alerts.length - seen),
      add,
      markSeen,
    }),
    [alerts, seen, add, markSeen],
  );
}
