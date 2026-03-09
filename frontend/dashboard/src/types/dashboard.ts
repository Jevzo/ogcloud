export interface DashboardOverviewStats {
    totalPlayers: number;
    maxPlayers: number;
    activeServers: number;
    maintenanceEnabled: boolean;
}

export interface DashboardOverviewGroup {
    name: string;
    mode: string;
    activeInstances: number;
    players: number;
    capacityPercent: number;
}

export interface DashboardOverviewScalingAction {
    id: string | null;
    groupId: string;
    action: string;
    reason: string;
    serverId: string | null;
    details: string | null;
    timestamp: string;
}

export interface DashboardOverviewResponse {
    stats: DashboardOverviewStats;
    groups: DashboardOverviewGroup[];
    scalingActions: DashboardOverviewScalingAction[];
}

export interface GroupListItem {
    id: string;
    type: string;
    maintenance: boolean;
}

export interface PaginatedResponse<T> {
    items: T[];
    page: number;
    size: number;
    totalItems: number;
}

const normalizePositiveInteger = (value: number, fallback: number) => {
    if (!Number.isFinite(value)) {
        return fallback;
    }

    const nextValue = Math.trunc(value);
    return nextValue > 0 ? nextValue : fallback;
};

export const getPaginatedTotalPages = <T>(response: PaginatedResponse<T>) => {
    const pageSize = normalizePositiveInteger(response.size, 1);
    const totalItems = normalizePositiveInteger(response.totalItems, 0);

    if (totalItems === 0) {
        return 1;
    }

    return Math.max(1, Math.ceil(totalItems / pageSize));
};

export const getPaginatedHasNext = <T>(response: PaginatedResponse<T>) => {
    const currentPage = Math.max(0, Math.trunc(response.page));
    return currentPage + 1 < getPaginatedTotalPages(response);
};
