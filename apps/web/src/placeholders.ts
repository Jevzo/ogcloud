import type {
  AuditEntryView,
  GroupOverview,
  GroupView,
  JobView,
  LoadHistoryView,
  LoginView,
  MeView,
  NetworkSettingsView,
  NpcView,
  OAuthClientView,
  OnlinePlayerView,
  OverviewView,
  PasskeyView,
  PermissionGroupView,
  PlayerView,
  RoleView,
  RuntimeProfileView,
  ScalingLogView,
  ServerView,
  TemplateVersionView,
  UserView,
} from "@ogcloud/api-client";
import { newGroupSettings } from "./pages/groups/group-defaults.ts";

/*
 * What a view renders while its data is on its way (D-0228): typical values in the contract's shapes, drawn as
 * skeletons of their own size. Typed by the generated client, so a contract change that breaks one fails the build.
 */

const AT = "2026-01-01T00:00:00Z";
const ID = "01900000-0000-7000-8000-000000000000";
const USER = `user:${ID}`;
const PROFILE = "paper-26.3";

export const placeholderServer: ServerView = {
  id: ID,
  displayName: "lobby-000000",
  groupId: "lobby",
  groupType: "DYNAMIC",
  profileId: PROFILE,
  state: "RUNNING",
  stateSince: AT,
  players: 12,
  maxPlayers: 50,
  gameState: "LOBBY",
  startedAt: AT,
  lastHeartbeatAt: AT,
};

export const placeholderGroupOverview: GroupOverview = {
  id: "lobby",
  type: "DYNAMIC",
  maintenance: false,
  crashLoopPaused: false,
  running: 2,
  pending: 0,
  players: 12,
  capacity: 100,
};

export const placeholderGroup: GroupView = {
  id: "lobby",
  type: "DYNAMIC",
  settings: { ...newGroupSettings("DYNAMIC", []), profileId: PROFILE },
  activeTemplateVersionId: ID,
  version: 0,
};

export const placeholderPlayer: PlayerView = {
  uuid: ID,
  name: "Steve",
  permissionGroupId: "default",
  rankExpiresAt: null,
  firstSeenAt: AT,
  lastSeenAt: AT,
  session: {
    proxyId: "proxy-000000",
    serverId: "lobby-000000",
    permissionGroupId: "default",
    connectedAt: AT,
  },
};

export const placeholderOnlinePlayer: OnlinePlayerView = {
  uuid: placeholderPlayer.uuid,
  name: placeholderPlayer.name,
  session: {
    proxyId: "proxy-000000",
    serverId: "lobby-000000",
    permissionGroupId: "default",
    connectedAt: AT,
  },
};

export const placeholderPermissionGroup: PermissionGroupView = {
  id: "default",
  settings: {
    name: "Default",
    weight: 0,
    defaultGroup: false,
    display: {
      chatPrefix: "",
      chatSuffix: "",
      nameFormat: "{name}",
      tabPrefix: "",
    },
    permissions: {},
  },
  version: 0,
};

export const placeholderNpc: NpcView = {
  id: ID,
  settings: {
    groupId: "lobby",
    location: { world: "world", x: 0, y: 64, z: 0, yaw: 0, pitch: 0 },
    title: "Lobby",
    subtitle: "",
    skin: null,
    lookAtPlayers: true,
    leftClick: { groupId: "lobby" },
    rightClick: null,
  },
};

export const placeholderScaling: ScalingLogView = {
  id: ID,
  occurredAt: AT,
  groupId: "lobby",
  action: "START",
  serverId: null,
  reason: "MIN_ONLINE",
  details: "",
};

export const placeholderAudit: AuditEntryView = {
  id: ID,
  occurredAt: AT,
  actor: USER,
  action: "PUT /api/v1/groups/{id}",
  targetType: "groups",
  targetId: "lobby",
  summary: "",
  metadata: {},
};

export const placeholderJob: JobView = {
  id: ID,
  kind: "group.restart",
  state: "RUNNING",
  payload: { group: "lobby" },
  completedSteps: 1,
  totalSteps: 3,
  message: "",
  createdBy: USER,
  createdAt: AT,
  startedAt: AT,
  finishedAt: null,
};

export const placeholderUser: UserView = {
  id: ID,
  username: "admin",
  email: "admin@example.com",
  roleId: "admin",
  enabled: true,
  linkedPlayerUuid: null,
  linkedPlayerName: null,
  createdAt: AT,
  version: 0,
};

export const placeholderRole: RoleView = {
  id: "moderator",
  name: "Moderator",
  builtIn: true,
  permissions: [],
  version: 0,
};

export const placeholderClient: OAuthClientView = {
  clientId: "ogcloud-ci",
  name: "CI pipeline",
  scopes: ["servers:read"],
  firstParty: false,
  createdAt: AT,
};

export const placeholderTemplate: TemplateVersionView = {
  id: ID,
  sha256: "0".repeat(64),
  sizeBytes: 1_048_576,
  source: "UPLOAD",
  createdBy: USER,
  createdAt: AT,
  active: false,
};

export const placeholderRuntime: RuntimeProfileView = {
  id: PROFILE,
  platform: "PAPER",
  role: "BACKEND",
  version: "26.3",
  protocol: 775,
  javaVersion: 25,
  artifacts: [],
};

export const placeholderOverview: OverviewView = {
  playersOnline: 12,
  maxPlayers: 500,
  maintenance: false,
  eulaAccepted: true,
  serversByState: { RUNNING: 2 },
  groups: [placeholderGroupOverview, placeholderGroupOverview],
  recentScaling: [placeholderScaling, placeholderScaling],
};

export const placeholderNetwork: NetworkSettingsView = {
  settings: {
    maintenance: false,
    motd: "<gray>A Minecraft network",
    maintenanceMotd: "<gray>A Minecraft network",
    versionName: "OgCloud",
    maintenanceVersionName: "Maintenance",
    maxPlayers: 500,
    defaultGroupId: "lobby",
    routingStrategy: "LEAST_FILLED",
    permissionSystemEnabled: true,
    tablistEnabled: true,
    tablistHeader: "",
    tablistFooter: "",
    maintenanceKickMessage: "",
  },
  eulaAcceptedAt: AT,
  version: 0,
};

export const placeholderLogin: LoginView = {
  id: ID,
  clientId: "ogcloud-web",
  signedInAt: AT,
  lastActiveAt: AT,
  expiresAt: AT,
  current: false,
};

export const placeholderPasskey: PasskeyView = {
  id: ID,
  label: "MacBook",
  createdAt: AT,
  lastUsedAt: AT,
};

export const placeholderMe: MeView = {
  id: ID,
  username: "admin",
  email: "admin@example.com",
  roleId: "admin",
  permissions: [],
  linkedPlayerUuid: null,
  linkedPlayerName: null,
  totpEnabled: false,
};

const HOUR_MS = 3_600_000;
const HISTORY_HOURS = 24;

/** A day of samples ending [now], so every range of a history chart has points to draw as a skeleton. */
export function placeholderHistory(now: number): LoadHistoryView {
  return {
    intervalSeconds: 60,
    points: Array.from({ length: HISTORY_HOURS + 1 }, (_, hour) => ({
      at: new Date(now - (HISTORY_HOURS - hour) * HOUR_MS).toISOString(),
      players: 1,
      running: 1,
    })),
  };
}
