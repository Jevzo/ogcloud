interface PermissionGroupDisplayConfig {
    chatPrefix: string;
    chatSuffix: string;
    nameColor: string;
    tabPrefix: string;
}

export interface PermissionGroupRecord {
    id: string;
    name: string;
    display: PermissionGroupDisplayConfig;
    weight: number;
    default: boolean;
    permissions: string[];
}

export interface CreatePermissionGroupPayload {
    id: string;
    name: string;
    display: PermissionGroupDisplayConfig;
    weight: number;
    default: boolean;
    permissions: string[];
}

export interface UpdatePermissionGroupPayload {
    name?: string;
    display?: PermissionGroupDisplayConfig;
    weight?: number;
    default?: boolean;
    permissions?: string[];
}

export interface PermissionGroupFormValues {
    id: string;
    name: string;
    weight: string;
    default: boolean;
    display: PermissionGroupDisplayConfig;
}
