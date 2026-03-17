import { z } from "zod";

export const searchGroupResultSchema = z.object({
    id: z.string().min(1),
    type: z.string().min(1),
    maintenance: z.boolean(),
});

export const searchServerResultSchema = z.object({
    id: z.string().min(1),
    group: z.string().min(1),
    type: z.string().min(1),
    displayName: z.string(),
    podName: z.string(),
    state: z.string().min(1),
});

export const searchPlayerResultSchema = z.object({
    uuid: z.string().min(1),
    name: z.string().min(1),
    permissionGroup: z.string().min(1),
    firstJoin: z.string().min(1),
    online: z.boolean(),
    proxyId: z.string().min(1).nullable(),
    serverId: z.string().min(1).nullable(),
    connectedAt: z.string().min(1).nullable(),
});

export const searchResponseSchema = z.object({
    query: z.string(),
    limit: z.number().int().positive(),
    groups: z.array(searchGroupResultSchema),
    servers: z.array(searchServerResultSchema),
    players: z.array(searchPlayerResultSchema),
});

export type SearchGroupResult = z.infer<typeof searchGroupResultSchema>;
export type SearchServerResult = z.infer<typeof searchServerResultSchema>;
export type SearchPlayerResult = z.infer<typeof searchPlayerResultSchema>;
export type SearchResponse = z.infer<typeof searchResponseSchema>;

export const createEmptySearchResponse = (limit: number): SearchResponse => ({
    query: "",
    limit,
    groups: [],
    servers: [],
    players: [],
});
