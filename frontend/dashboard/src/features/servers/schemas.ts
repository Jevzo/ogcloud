import { z } from "zod";

import { createPaginatedResponseSchema } from "@/lib/api/shared";
import { GROUP_TYPE_VALUES } from "@/types/group";

export const serverRecordSchema = z.object({
    id: z.string().min(1),
    group: z.string().min(1),
    type: z.enum(GROUP_TYPE_VALUES),
    displayName: z.string(),
    state: z.string().min(1),
    gameState: z.string().min(1).nullable(),
    podName: z.string().min(1),
    podIp: z.string().min(1).nullable(),
    port: z.number().int().nonnegative(),
    templateVersion: z.string().min(1),
    playerCount: z.number().int().nonnegative(),
    maxPlayers: z.number().int().nonnegative(),
    tps: z.number().finite(),
    memoryUsedMb: z.number().nonnegative(),
    startedAt: z.string().min(1).nullable(),
    lastHeartbeat: z.string().min(1).nullable(),
});

export const onlinePlayerRecordSchema = z.object({
    uuid: z.string().min(1),
    name: z.string().min(1),
    proxyId: z.string().min(1).nullable(),
    proxyDisplayName: z.string().min(1).nullable(),
    serverId: z.string().min(1).nullable(),
    serverDisplayName: z.string().min(1).nullable(),
    groupId: z.string().min(1).nullable(),
    connectedAt: z.string().min(1).nullable(),
});

export const serverRequestResponseSchema = z.object({
    serverId: z.string().min(1),
    group: z.string().min(1),
});

export const serversPageSchema = createPaginatedResponseSchema(serverRecordSchema);
export const onlinePlayersPageSchema = createPaginatedResponseSchema(onlinePlayerRecordSchema);

export const deployServerFormSchema = z.object({
    groupId: z.string().min(1, "Select a group."),
    count: z
        .number({ message: "Enter how many instances to request." })
        .int("Instance count must be a whole number.")
        .min(1, "Request at least one instance.")
        .max(25, "Request 25 instances or fewer per action."),
});

export const executeCommandFormSchema = z.object({
    command: z
        .string()
        .trim()
        .min(1, "Enter a command first.")
        .max(512, "Keep commands under 512 characters."),
});

export type ServerRecordSchema = z.infer<typeof serverRecordSchema>;
export type OnlinePlayerRecordSchema = z.infer<typeof onlinePlayerRecordSchema>;
export type ServerRequestResponseSchema = z.infer<typeof serverRequestResponseSchema>;
export type DeployServerFormValues = z.infer<typeof deployServerFormSchema>;
export type ExecuteCommandFormValues = z.infer<typeof executeCommandFormSchema>;
