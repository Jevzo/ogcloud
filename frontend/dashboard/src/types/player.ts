interface PlayerPermissionConfig {
  group: string;
  length: number;
  endMillis: number;
}

export interface PersistedPlayerRecord {
  uuid: string;
  name: string;
  permission: PlayerPermissionConfig;
  firstJoin: string;
  online: boolean;
  proxyId: string | null;
  serverId: string | null;
  connectedAt: string | null;
}

export interface PlayerRecord extends PersistedPlayerRecord {
  proxyDisplayName?: string | null;
  serverDisplayName?: string | null;
}
