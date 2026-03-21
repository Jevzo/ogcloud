interface PermissionGroupDisplayConfig {
    chatPrefix: string;
    chatSuffix: string;
    nameColor: string;
    tabPrefix: string;
}

export interface PermissionGroupPermissionRecord {
    perm: string;
    description: string;
}

export interface PermissionGroupPermissionPayload {
    perm: string;
    description: string;
}

export interface PermissionGroupRecord {
    id: string;
    name: string;
    display: PermissionGroupDisplayConfig;
    weight: number;
    default: boolean;
    permissions: PermissionGroupPermissionRecord[];
}

export interface CreatePermissionGroupPayload {
    id: string;
    name: string;
    display: PermissionGroupDisplayConfig;
    weight: number;
    default: boolean;
    permissions: PermissionGroupPermissionPayload[];
}

export interface UpdatePermissionGroupPayload {
    name?: string;
    display?: PermissionGroupDisplayConfig;
    weight?: number;
    default?: boolean;
    permissions?: PermissionGroupPermissionPayload[];
}

export interface PermissionGroupFormValues {
    id: string;
    name: string;
    weight: string;
    default: boolean;
    display: PermissionGroupDisplayConfig;
}
