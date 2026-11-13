import type { NavPath } from "../navigation.ts";
import { readStored, writeStored } from "../stored.ts";

/** Somewhere ⌘K leads: a page, or a group, server or player found by its search. */
export type Target =
  | { kind: "page"; path: NavPath }
  | { kind: "group"; id: string }
  | { kind: "server"; id: string; name: string; groupId: string }
  | { kind: "player"; uuid: string; name: string };

const KEY = "ogcloud.recent";
const KEEP = 5;

export function targetKey(target: Target): string {
  switch (target.kind) {
    case "page":
      return `page:${target.path}`;
    case "player":
      return `player:${target.uuid}`;
    default:
      return `${target.kind}:${target.id}`;
  }
}

const isText = (value: unknown) => typeof value === "string";

// Storage is the user's to edit, so every remembered entry is checked before it is shown.
function isTarget(value: unknown): value is Target {
  if (typeof value !== "object" || value === null) return false;
  const entry = value as Record<string, unknown>;
  switch (entry.kind) {
    case "page":
      return isText(entry.path);
    case "group":
      return isText(entry.id);
    case "server":
      return isText(entry.id) && isText(entry.name) && isText(entry.groupId);
    case "player":
      return isText(entry.uuid) && isText(entry.name);
    default:
      return false;
  }
}

/** What ⌘K led to lately on this device, newest first. */
export function readRecent(): Target[] {
  const stored = readStored(KEY);
  return Array.isArray(stored) ? stored.filter(isTarget) : [];
}

export function remember(target: Target): void {
  const key = targetKey(target);
  writeStored(
    KEY,
    [target, ...readRecent().filter((entry) => targetKey(entry) !== key)].slice(
      0,
      KEEP,
    ),
  );
}
