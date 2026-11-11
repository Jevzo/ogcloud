import type {
  LoadHistoryView,
  MeView,
  OverviewView,
  ScalingLogView,
} from "@ogcloud/api-client";

// Fixed answers for the screenshots, so pages look the same on every run; everything else comes from orval's mocks.

/** The moment the screenshots pretend it is (their browser clock is fixed to it); fixtures count back from it. */
export const MOCK_NOW = Date.parse("2026-11-11T18:40:00Z");
const MINUTE_MS = 60_000;
const minutesAgo = (minutes: number) =>
  new Date(MOCK_NOW - minutes * MINUTE_MS).toISOString();

const ALL_PERMISSIONS = [
  "overview:read",
  "servers:read",
  "servers:command",
  "groups:read",
  "groups:write",
  "players:read",
  "players:write",
  "network:read",
  "network:write",
  "permissions:read",
  "permissions:write",
  "templates:read",
  "templates:write",
  "npcs:read",
  "npcs:write",
  "logs:read",
  "users:read",
  "users:write",
  "roles:read",
  "roles:write",
  "oauth-clients:read",
  "oauth-clients:write",
  "privacy:export",
  "privacy:erase",
];

export const me: MeView = {
  id: "0195b1d2-7c4e-7a10-9f3e-2b8d4c6a1e01",
  username: "mira",
  email: "mira@ogwars.io",
  roleId: "owner",
  permissions: ALL_PERMISSIONS,
  linkedPlayerUuid: "5f1c2a9e-3b7d-4e8f-a1c2-9d8e7f6a5b4c",
  linkedPlayerName: "mira",
  totpEnabled: true,
};

export const overview: OverviewView = {
  playersOnline: 1284,
  maxPlayers: 5000,
  maintenance: false,
  eulaAccepted: true,
  serversByState: { RUNNING: 23, STARTING: 1, DRAINING: 1 },
  groups: [
    {
      id: "proxy",
      type: "PROXY",
      maintenance: false,
      crashLoopPaused: false,
      running: 2,
      pending: 0,
      players: 1284,
      capacity: 2000,
    },
    {
      id: "lobby",
      type: "DYNAMIC",
      maintenance: false,
      crashLoopPaused: false,
      running: 5,
      pending: 0,
      players: 412,
      capacity: 500,
    },
    {
      id: "bedwars",
      type: "DYNAMIC",
      maintenance: false,
      crashLoopPaused: false,
      running: 14,
      pending: 1,
      players: 828,
      capacity: 896,
    },
    {
      id: "skywars",
      type: "DYNAMIC",
      maintenance: false,
      crashLoopPaused: true,
      running: 0,
      pending: 0,
      players: 0,
      capacity: 0,
    },
    {
      id: "smp",
      type: "STATIC",
      maintenance: false,
      crashLoopPaused: false,
      running: 1,
      pending: 0,
      players: 41,
      capacity: 100,
    },
    {
      id: "build",
      type: "STATIC",
      maintenance: true,
      crashLoopPaused: false,
      running: 1,
      pending: 0,
      players: 3,
      capacity: 20,
    },
  ],
  recentScaling: scaling(),
};

function scaling(): ScalingLogView[] {
  const entries: [
    number,
    ScalingLogView["action"],
    string,
    ScalingLogView["reason"],
  ][] = [
    [2, "START", "bedwars", "LOAD"],
    [9, "DRAIN", "lobby", "SCALE_DOWN"],
    [14, "START", "lobby", "MIN_ONLINE"],
    [38, "UNDRAIN", "proxy", "LOAD"],
    [71, "STOP", "bedwars", "SCALE_DOWN"],
  ];
  return entries.map(([minutes, action, groupId, reason], index) => ({
    id: `0195b1d2-7c4e-7a10-9f3e-00000000000${index}`,
    occurredAt: minutesAgo(minutes),
    groupId,
    action,
    serverId: null,
    reason,
    details: "",
  }));
}

const DAY_MINUTES = 24 * 60;
const EVENING_PEAK_HOUR = 20.5;

/** A day of players a minute apart: quiet mornings, a peak in the evening, a little jitter. */
export function loadHistory(): LoadHistoryView {
  const points = Array.from({ length: DAY_MINUTES + 1 }, (_, index) => {
    const minutes = DAY_MINUTES - index;
    const at = new Date(MOCK_NOW - minutes * MINUTE_MS);
    const hour = at.getUTCHours() + at.getUTCMinutes() / 60;
    const evening = Math.cos(((hour - EVENING_PEAK_HOUR) / 24) * 2 * Math.PI);
    const jitter = Math.sin(index * 0.37) * 18 + Math.sin(index * 0.051) * 35;
    const players = Math.max(0, Math.round(760 + 640 * evening + jitter));
    return {
      at: at.toISOString(),
      players: minutes === 0 ? overview.playersOnline : players,
      running: Math.max(4, Math.round(players / 56)),
    };
  });
  return { intervalSeconds: 60, points };
}
