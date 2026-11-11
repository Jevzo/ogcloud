import type {
  DeletedGroupView,
  GroupView,
  LoadHistoryView,
  MeView,
  NetworkSettingsView,
  OnlinePlayerView,
  OverviewView,
  PermissionGroupView,
  PlayerView,
  RuntimeProfileView,
  ScalingLogView,
  ServerView,
  TextPreview,
  TextPreviewRequest,
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

interface Fleet {
  group: string;
  type: ServerView["groupType"];
  profile: string;
  maxPlayers: number;
  players: readonly number[];
  states?: Partial<Record<number, ServerView["state"]>>;
  games?: readonly ServerView["gameState"][];
}

const FLEETS: readonly Fleet[] = [
  {
    group: "proxy",
    type: "PROXY",
    profile: "velocity-4.3.0",
    maxPlayers: 1000,
    players: [650, 634],
  },
  {
    group: "lobby",
    type: "DYNAMIC",
    profile: "paper-26.3",
    maxPlayers: 100,
    players: [96, 88, 81, 79, 68, 0],
    states: { 5: "DRAINING" },
  },
  {
    group: "bedwars",
    type: "DYNAMIC",
    profile: "paper-26.3",
    maxPlayers: 64,
    players: [64, 64, 62, 64, 60, 64, 58, 64, 56, 64, 52, 60, 48, 48, 0],
    states: { 14: "STARTING" },
    games: ["INGAME", "INGAME", "LOBBY", "INGAME", "LOBBY", "ENDING"],
  },
  {
    group: "smp",
    type: "STATIC",
    profile: "paper-26.3",
    maxPlayers: 100,
    players: [41],
  },
  {
    group: "build",
    type: "STATIC",
    profile: "pandaspigot-1.8.8",
    maxPlayers: 20,
    players: [3],
  },
];

/** The 25 live servers the overview counts, with ids that look like the controller's UUIDv7s. */
export const servers: readonly ServerView[] = FLEETS.flatMap((fleet, f) =>
  fleet.players.map((players, index) => {
    const tail = (0x3fa9c1 + f * 0x10f3 + index * 0x2b7d)
      .toString(16)
      .padStart(6, "0");
    const id = `0195b1d2-7c4e-7a10-9f3e-2b8d4c${tail}`;
    const state = fleet.states?.[index] ?? "RUNNING";
    const started = state === "STARTING" ? null : minutesAgo(30 + index * 47);
    return {
      id,
      displayName: `${fleet.group}-${tail}`,
      groupId: fleet.group,
      groupType: fleet.type,
      profileId: fleet.profile,
      state,
      stateSince: minutesAgo(state === "RUNNING" ? 25 + index * 47 : 1),
      players,
      maxPlayers: fleet.maxPlayers,
      gameState:
        fleet.type === "DYNAMIC"
          ? (fleet.games?.[index % fleet.games.length] ?? "LOBBY")
          : null,
      startedAt: started,
      lastHeartbeatAt: state === "STARTING" ? null : minutesAgo(0.05),
    };
  }),
);

/** A server by id; unknown ids get the first one, since mocks have no live state to miss. */
export function serverById(id: string): ServerView {
  const found = servers.find((server) => server.id === id) ?? servers[0];
  if (found === undefined) throw new Error("The server fixture is empty");
  return found;
}

const ARTIFACT = (path: string, minutes: number) => ({
  path,
  sha256: "9f2c4e7a1b3d5f60718293a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4",
  sizeBytes: 4_812_345,
  sourceUrl: `https://cdn.modrinth.com/${path}`,
  storedAt: minutesAgo(minutes),
});

export const runtimeProfiles: RuntimeProfileView[] = [
  {
    id: "paper-26.3",
    platform: "PAPER",
    role: "BACKEND",
    version: "26.3",
    protocol: 777,
    javaVersion: 25,
    artifacts: [
      ARTIFACT("plugins/ogcloud-paper.jar", 300),
      ARTIFACT("plugins/packetevents.jar", 4000),
    ],
  },
  {
    id: "pandaspigot-1.8.8",
    platform: "PANDASPIGOT",
    role: "BACKEND",
    version: "1.8.8",
    protocol: 47,
    javaVersion: 25,
    artifacts: [],
  },
  {
    id: "velocity-4.3.0",
    platform: "VELOCITY",
    role: "PROXY",
    version: "4.3.0",
    protocol: 0,
    javaVersion: 25,
    artifacts: [
      ARTIFACT("plugins/ogcloud-velocity.jar", 300),
      ARTIFACT("plugins/ViaVersion.jar", 9000),
      ARTIFACT("plugins/ViaBackwards.jar", 9000),
      ARTIFACT("velocity.toml", 300),
    ],
  },
];

/** The overview's groups with settings a real network might use. */
export const groups: GroupView[] = FLEETS.map((fleet, index) => ({
  id: fleet.group,
  type: fleet.type,
  version: 3 + index,
  activeTemplateVersionId: null,
  settings: {
    profileId: fleet.profile,
    scaling: {
      minOnline: fleet.type === "DYNAMIC" ? 2 : 1,
      maxInstances:
        fleet.type === "STATIC" ? 1 : fleet.type === "PROXY" ? 4 : 20,
      playersPerServer: fleet.maxPlayers,
      scaleUpThreshold: 0.8,
      scaleDownThreshold: 0.3,
      cooldownSeconds: 60,
    },
    drainTimeoutSeconds: 60,
    maintenance: fleet.group === "build",
    resources: {
      memoryRequest: fleet.type === "PROXY" ? "1Gi" : "2Gi",
      memoryLimit: fleet.type === "PROXY" ? "1Gi" : "2Gi",
      cpuRequest: "1",
      cpuLimit: null,
    },
    jvmFlags: fleet.type === "PROXY" ? "" : "-XX:+UseZGC -XX:+ZGenerational",
    image: null,
    storageSize: fleet.type === "STATIC" ? "10Gi" : null,
  },
})).concat({
  id: "skywars",
  type: "DYNAMIC",
  version: 9,
  activeTemplateVersionId: null,
  settings: {
    profileId: "paper-26.3",
    scaling: {
      minOnline: 2,
      maxInstances: 12,
      playersPerServer: 16,
      scaleUpThreshold: 0.75,
      scaleDownThreshold: 0.25,
      cooldownSeconds: 45,
    },
    drainTimeoutSeconds: 30,
    maintenance: false,
    resources: {
      memoryRequest: "2Gi",
      memoryLimit: "2Gi",
      cpuRequest: "1",
      cpuLimit: null,
    },
    jvmFlags: "",
    image: null,
    storageSize: null,
  },
});

/** A group by id; unknown ids get the first one. */
export function groupById(id: string): GroupView {
  const found = groups.find((group) => group.id === id) ?? groups[0];
  if (found === undefined) throw new Error("The group fixture is empty");
  return found;
}

export const deletedGroups: DeletedGroupView[] = [
  {
    group: { ...groupById("lobby"), id: "halloween" },
    deletedAt: minutesAgo(190),
  },
];

const NAMES = [
  "mira",
  "kiwibear",
  "Tobbe04",
  "redstoneRita",
  "Nachtfalke",
  "pixelpaul",
  "Lumi_",
  "creeperKarl",
  "sunny_side",
  "B4rt",
  "frostbyte",
  "Malachit",
  "zoe_builds",
  "Hexenmeister",
  "tinyturtle",
  "Orbital",
  "jules",
  "MossyStone",
];
const RANKS = [
  "owner",
  "admin",
  "vip",
  "default",
  "default",
  "default",
  "vip",
  "default",
];
const backends = servers.filter(
  (server) => server.groupType !== "PROXY" && server.state === "RUNNING",
);
const proxies = servers.filter((server) => server.groupType === "PROXY");

/** Players online now, spread over the running servers. */
export const onlinePlayers: OnlinePlayerView[] = NAMES.map((name, index) => ({
  uuid: `5f1c2a9e-3b7d-4e8f-a1c2-9d8e7f6a5b${index.toString(16).padStart(2, "0")}`,
  name,
  session: {
    proxyId: proxies[index % proxies.length]?.id ?? "",
    serverId: backends[(index * 5) % backends.length]?.id ?? null,
    permissionGroupId: RANKS[index % RANKS.length] ?? "default",
    connectedAt: minutesAgo(3 + index * 11),
  },
}));

export const knownPlayers: PlayerView[] = onlinePlayers.map(
  (player, index) => ({
    uuid: player.uuid,
    name: player.name,
    permissionGroupId: player.session.permissionGroupId,
    rankExpiresAt:
      player.session.permissionGroupId === "vip"
        ? new Date(MOCK_NOW + (index + 3) * 86_400_000).toISOString()
        : null,
    firstSeenAt: minutesAgo(60 * 24 * (20 + index * 9)),
    lastSeenAt: minutesAgo(index * 7),
    session: null,
  }),
);

/** A player by id, online with their session when they are. */
export function playerById(uuid: string): PlayerView {
  const known =
    knownPlayers.find((player) => player.uuid === uuid) ?? knownPlayers[0];
  if (known === undefined) throw new Error("The player fixture is empty");
  const online = onlinePlayers.find((player) => player.uuid === known.uuid);
  return { ...known, session: online?.session ?? null };
}

export const permissionGroups: PermissionGroupView[] = [
  {
    id: "owner",
    version: 4,
    settings: {
      name: "Owner",
      weight: 1000,
      defaultGroup: false,
      display: {
        chatPrefix: "<gradient:#ff5555:#ffaa00>[Owner]</gradient> ",
        chatSuffix: "",
        nameFormat: "<gold>{name}",
        tabPrefix: "<gradient:#ff5555:#ffaa00>Owner</gradient> ",
      },
      permissions: {
        "*": "Everything",
        "ogcloud.admin.command": "Console commands",
      },
    },
  },
  {
    id: "admin",
    version: 3,
    settings: {
      name: "Admin",
      weight: 500,
      defaultGroup: false,
      display: {
        chatPrefix: "<red>[Admin]</red> ",
        chatSuffix: "",
        nameFormat: "<red>{name}",
        tabPrefix: "<red>Admin ",
      },
      permissions: {
        "ogcloud.admin.server": "Server commands",
        "ogcloud.maintenance.bypass": "Join in maintenance",
      },
    },
  },
  {
    id: "vip",
    version: 2,
    settings: {
      name: "VIP",
      weight: 100,
      defaultGroup: false,
      display: {
        chatPrefix: "<green>[VIP]</green> ",
        chatSuffix: "",
        nameFormat: "<green>{name}",
        tabPrefix: "<green>VIP ",
      },
      permissions: {
        "lobby.fly": "Fly in the lobby",
        "cosmetics.trails": "Particle trails",
      },
    },
  },
  {
    id: "default",
    version: 1,
    settings: {
      name: "Player",
      weight: 0,
      defaultGroup: true,
      display: {
        chatPrefix: "",
        chatSuffix: "",
        nameFormat: "<gray>{name}",
        tabPrefix: "<gray>",
      },
      permissions: {},
    },
  },
];

const TAG = /<[^>]*>/g;
const HEX = /#[0-9a-fA-F]{6}/;
const NAMED: Record<string, string> = {
  gold: "#FFAA00",
  green: "#55FF55",
  gray: "#AAAAAA",
  red: "#FF5555",
  yellow: "#FFFF55",
  aqua: "#55FFFF",
};
const PLACEHOLDERS: Partial<Record<TextPreviewRequest["field"], string[]>> = {
  NAME_FORMAT: ["name"],
  TABLIST_HEADER: [
    "onlinePlayers",
    "maxPlayers",
    "server",
    "group",
    "proxy",
    "ping",
    "permissionGroup",
  ],
  TABLIST_FOOTER: [
    "onlinePlayers",
    "maxPlayers",
    "server",
    "group",
    "proxy",
    "ping",
    "permissionGroup",
  ],
};

/** The mocks draw a text in its first colour with its tags removed; the real API renders every tag (D-0202). */
export function previewOf(request: TextPreviewRequest): TextPreview {
  const named = /<([a-z_]+)>/.exec(request.text)?.[1];
  const color =
    HEX.exec(request.text)?.[0] ??
    (named === undefined ? null : (NAMED[named] ?? null));
  const span = {
    text: request.text
      .replace(TAG, "")
      .replaceAll("{name}", "Steve")
      .replaceAll("{onlinePlayers}", "1,284"),
    color,
    bold: false,
    italic: false,
    underlined: false,
    strikethrough: false,
    obfuscated: false,
  };
  return {
    errors: [],
    rendering: { modern: [span], legacy: [span] },
    placeholders: PLACEHOLDERS[request.field] ?? [],
  };
}

/** A permission group by id; unknown ids get the first one. */
export function permissionGroupById(id: string): PermissionGroupView {
  const found =
    permissionGroups.find((group) => group.id === id) ?? permissionGroups[0];
  if (found === undefined)
    throw new Error("The permission group fixture is empty");
  return found;
}

export const networkSettings: NetworkSettingsView = {
  version: 12,
  eulaAcceptedAt: minutesAgo(60 * 24 * 40),
  settings: {
    maintenance: false,
    motd: "<gradient:#ff8800:#ff3d7f>OgWars Network</gradient> <gray>1.8 to 26.3\n<yellow>Bedwars season 4 is live",
    maintenanceMotd:
      "<red>We are updating the network\n<gray>Back in a few minutes",
    versionName: "<gray>OgWars 1.8 to 26.3",
    maintenanceVersionName: "<red>Maintenance",
    maxPlayers: 5000,
    defaultGroupId: "lobby",
    routingStrategy: "BALANCED",
    permissionSystemEnabled: true,
    tablistEnabled: true,
    tablistHeader:
      "<gradient:#ff8800:#ff3d7f>OgWars Network</gradient>\n<gray>{server}",
    tablistFooter: "<yellow>{onlinePlayers}<gray> players online",
    maintenanceKickMessage: "<red>The network is in maintenance",
    requireMinecraftLink: false,
  },
};
