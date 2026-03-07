interface NetworkTextPair {
  global: string;
  maintenance: string;
}

interface NetworkTablistSettings {
  header: string;
  footer: string;
}

export interface NetworkGeneralSettings {
  permissionSystemEnabled: boolean;
  tablistEnabled: boolean;
}

export interface NetworkSettingsRecord {
  motd: NetworkTextPair;
  versionName: NetworkTextPair;
  maxPlayers: number;
  defaultGroup: string;
  maintenance: boolean;
  maintenanceKickMessage: string;
  tablist: NetworkTablistSettings;
  general: NetworkGeneralSettings;
}

export interface NetworkStatusRecord {
  onlinePlayers: number;
  serverCount: number;
  proxyCount: number;
}

export interface UpdateNetworkPayload {
  motd?: Partial<NetworkTextPair>;
  versionName?: Partial<NetworkTextPair>;
  maxPlayers?: number;
  defaultGroup?: string;
  maintenance?: boolean;
  maintenanceKickMessage?: string;
  tablist?: Partial<NetworkTablistSettings>;
  general?: Partial<NetworkGeneralSettings>;
}
