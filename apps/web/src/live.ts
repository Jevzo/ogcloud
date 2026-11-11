import { eventTicket } from "@ogcloud/api-client";
import {
  type ApiConnection,
  ApiConnectionContext,
} from "@ogcloud/api-client/react";
import { useQueryClient } from "@tanstack/react-query";
import { useContext, useEffect, useState } from "react";
import { invalidatePaths } from "./queries.ts";

export type LiveState = "connecting" | "connected" | "reconnecting";

export interface Alert {
  subjectId: string;
  kind: string;
}

// Which cached reads each event makes stale (D-0105); counts that heartbeats change are refetched on their own timer.
const STALE_AFTER: Record<string, readonly string[]> = {
  server: ["/api/v1/servers", "/api/v1/overview", "/api/v1/groups"],
  job: ["/api/v1/jobs"],
  scaling: ["/api/v1/logs/scaling", "/api/v1/overview"],
  alert: ["/api/v1/overview", "/api/v1/groups"],
};
const FIRST_RETRY_MS = 1_000;
const LONGEST_RETRY_MS = 30_000;

async function openStream(connection: ApiConnection): Promise<EventSource> {
  const ticket = await eventTicket({ token: await connection.accessToken() });
  return new EventSource(
    `/api/v1/events?ticket=${encodeURIComponent(ticket.data.ticket)}`,
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
              if (name === "alert")
                onAlert(
                  JSON.parse((event as MessageEvent<string>).data) as Alert,
                );
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
