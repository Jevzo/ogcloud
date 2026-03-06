export const SERVER_ACTION_KINDS = ["drain", "push", "kill"] as const;

export type ServerActionKind = (typeof SERVER_ACTION_KINDS)[number];

export interface ServerRecord {
  id: string;
  group: string;
  type: string;
  displayName: string;
  state: string;
  gameState: string | null;
  podName: string;
  podIp: string | null;
  port: number;
  templateVersion: string;
  playerCount: number;
  maxPlayers: number;
  tps: number;
  memoryUsedMb: number;
  startedAt: string | null;
  lastHeartbeat: string | null;
}

export interface OnlinePlayerRecord {
  uuid: string;
  name: string;
  proxyId: string | null;
  proxyDisplayName: string | null;
  serverId: string | null;
  serverDisplayName: string | null;
  groupId: string | null;
  connectedAt: string | null;
}
