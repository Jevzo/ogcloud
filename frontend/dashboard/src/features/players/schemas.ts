import { z } from "zod";

export const playerPermissionConfigSchema = z.object({
    group: z.string().min(1),
    length: z.number().int(),
    endMillis: z.number().int(),
});

export const persistedPlayerRecordSchema = z.object({
    uuid: z.string().min(1),
    name: z.string().min(1),
    permission: playerPermissionConfigSchema,
    firstJoin: z.string().min(1),
    online: z.boolean(),
    proxyId: z.string().min(1).nullable(),
    serverId: z.string().min(1).nullable(),
    connectedAt: z.string().min(1).nullable(),
});

export const playerRecordSchema = persistedPlayerRecordSchema.extend({
    proxyDisplayName: z.string().min(1).nullable().optional(),
    serverDisplayName: z.string().min(1).nullable().optional(),
});

export const playerPermissionAssignmentFormSchema = z.object({
    group: z.string().trim().min(1, "Choose a permission group first."),
    duration: z.string().trim().min(1, "Enter a duration such as -1, 30d, or 1h 30m."),
});

export const playerTransferFormSchema = z.object({
    target: z.string().trim().min(1, "Choose a target server first."),
});

export type PersistedPlayerRecordSchema = z.infer<typeof persistedPlayerRecordSchema>;
export type PlayerRecordSchema = z.infer<typeof playerRecordSchema>;
export type PlayerPermissionAssignmentFormValues = z.infer<
    typeof playerPermissionAssignmentFormSchema
>;
export type PlayerTransferFormValues = z.infer<typeof playerTransferFormSchema>;
