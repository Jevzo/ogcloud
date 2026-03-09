interface SearchGroupResult {
    id: string;
    type: string;
    maintenance: boolean;
}

interface SearchServerResult {
    id: string;
    group: string;
    type: string;
    displayName: string;
    podName: string;
    state: string;
}

interface SearchPlayerResult {
    uuid: string;
    name: string;
    permissionGroup: string;
    firstJoin: string;
    online: boolean;
    proxyId: string | null;
    serverId: string | null;
    connectedAt: string | null;
}

export interface SearchResponse {
    query: string;
    limit: number;
    groups: SearchGroupResult[];
    servers: SearchServerResult[];
    players: SearchPlayerResult[];
}
