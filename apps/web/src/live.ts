import { type AlertEvent, eventTicket } from "@ogcloud/api-client";
import {
  type ApiConnection,
  ApiConnectionContext,
  callOptions,
} from "@ogcloud/api-client/react";
import { useQueryClient } from "@tanstack/react-query";
import { useContext, useEffect, useState } from "react";
import { invalidatePaths } from "./queries.ts";

export type LiveState = "connecting" | "connected" | "reconnecting";

/** An alert as the event stream sends it; its type comes from the contract (D-0011). */
export type Alert = AlertEvent;

// Which cached reads each event makes stale (D-0105); counts that heartbeats change are refetched on their own timer.
const STALE_AFTER: Record<string, readonly string[]> = {
  server: ["/api/v1/servers", "/api/v1/overview", "/api/v1/groups"],
  job: ["/api/v1/jobs"],
  scaling: ["/api/v1/logs/scaling", "/api/v1/overview"],
  alert: ["/api/v1/overview", "/api/v1/groups"],
};
const FIRST_RETRY_MS = 1_000;
const LONGEST_RETRY_MS = 30_000;

/** An alert event's data, or none when it is not the contract's shape; such an event is logged and skipped. */
function parseAlert(data: string): Alert | undefined {
  try {
    const value: unknown = JSON.parse(data);
    if (isAlert(value)) return value;
    console.error("Skipping an alert event of an unknown shape", value);
  } catch (error) {
    console.error("Skipping an alert event that is not JSON", error);
  }
  return undefined;
}

function isAlert(value: unknown): value is Alert {
  if (typeof value !== "object" || value === null) return false;
  const event = value as Record<string, unknown>;
  return (
    typeof event.subjectId === "string" &&
    typeof event.kind === "string" &&
    typeof event.severity === "string" &&
    typeof event.attributes === "object" &&
    event.attributes !== null
  );
}

async function openStream(connection: ApiConnection): Promise<EventSource> {
  const call = await callOptions(connection);
  const ticket = await eventTicket(call);
  return new EventSource(
    `${call.origin ?? ""}/api/v1/events?ticket=${encodeURIComponent(ticket.data.ticket)}`,
  );
}

/**
 * Keeps one event stream open while the app runs: each stream needs a fresh single-use ticket, ends after 30 minutes
 * and is reopened with a growing pause after failures; delivery is at most once, so reconnects refetch (D-0105).
 */
export function useLiveEvents(
  enabled: boolean,
  onAlert: (alert: Alert) => void,
): LiveState {
  const connection = useContext(ApiConnectionContext);
  const client = useQueryClient();
  const [state, setState] = useState<LiveState>(
    enabled ? "connecting" : "connected",
  );

  useEffect(() => {
    if (connection === null || !enabled) return;
    let stopped = false;
    let source: EventSource | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let pause = FIRST_RETRY_MS;

    const retry = () => {
      source?.close();
      if (stopped) return;
      setState("reconnecting");
      timer = setTimeout(connect, pause);
      pause = Math.min(pause * 2, LONGEST_RETRY_MS);
    };
    const connect = () => {
      openStream(connection).then(
        (opened) => {
          if (stopped) {
            opened.close();
            return;
          }
          source = opened;
          opened.onopen = () => {
            setState("connected");
            pause = FIRST_RETRY_MS;
            void invalidatePaths(client, [
              ...new Set(Object.values(STALE_AFTER).flat()),
            ]);
          };
          opened.onerror = retry;
          for (const [name, prefixes] of Object.entries(STALE_AFTER)) {
            opened.addEventListener(name, (event) => {
              void invalidatePaths(client, prefixes);
              if (name !== "alert") return;
              const alert = parseAlert((event as MessageEvent<string>).data);
              if (alert !== undefined) onAlert(alert);
            });
          }
        },
        (error: unknown) => {
          console.warn("Could not open the event stream", error);
          retry();
        },
      );
    };
    connect();
    return () => {
      stopped = true;
      clearTimeout(timer);
      source?.close();
    };
  }, [connection, client, enabled, onAlert]);

  return state;
}
