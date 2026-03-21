import { z } from "zod";

import { NETWORK_MOTD_MAX_LINES, countTextLines } from "@/features/network/lib/utils";

export const proxyRoutingStrategySchema = z.enum(["ROUND_ROBIN", "LOAD_BASED"]);

export const networkGeneralSettingsSchema = z.object({
    permissionSystemEnabled: z.boolean(),
    tablistEnabled: z.boolean(),
    proxyRoutingStrategy: proxyRoutingStrategySchema,
});

export const networkSettingsApiSchema = z.object({
    motd: z.object({
        global: z.string(),
        maintenance: z.string(),
    }),
    versionName: z.object({
        global: z.string(),
        maintenance: z.string(),
    }),
    maxPlayers: z.number(),
    defaultGroup: z.string(),
    maintenance: z.boolean(),
    maintenanceKickMessage: z.string(),
    tablist: z.object({
        header: z.string(),
        footer: z.string(),
    }),
    general: z
        .object({
            permissionSystemEnabled: z.boolean().optional(),
            tablistEnabled: z.boolean().optional(),
            proxyRoutingStrategy: z.string().nullable().optional(),
        })
        .nullable()
        .optional(),
});

export const networkStatusSchema = z.object({
    onlinePlayers: z.number(),
    serverCount: z.number(),
    proxyCount: z.number(),
});

export const networkLockSchema = z.object({
    key: z.string(),
    type: z.string(),
    targetId: z.string().nullable(),
    token: z.string().nullable(),
    ttlSeconds: z.number().nullable(),
});

export const networkLocksResponseSchema = z.object({
    locks: z.array(networkLockSchema),
});

export const networkServerSettingsFormSchema = z
    .object({
        maxPlayers: z.string().trim().min(1, "Enter the network player cap."),
        defaultGroup: z.string().trim().min(1, "Select a non-proxy default group."),
    })
    .superRefine((values, context) => {
        const parsedMaxPlayers = Number.parseInt(values.maxPlayers, 10);

        if (!Number.isFinite(parsedMaxPlayers)) {
            context.addIssue({
                code: z.ZodIssueCode.custom,
                message: "Max players must be a valid number.",
                path: ["maxPlayers"],
            });
            return;
        }

        if (parsedMaxPlayers <= 0) {
            context.addIssue({
                code: z.ZodIssueCode.custom,
                message: "Max players must be greater than zero.",
                path: ["maxPlayers"],
            });
        }
    });

export const networkGeneralFormSchema = z.object({
    permissionSystemEnabled: z.boolean(),
    tablistEnabled: z.boolean(),
    proxyRoutingStrategy: proxyRoutingStrategySchema,
});

const motdSchema = z.string().refine((value) => countTextLines(value) <= NETWORK_MOTD_MAX_LINES, {
    message: `MOTD is limited to ${NETWORK_MOTD_MAX_LINES} lines.`,
});

export const networkMessagingFormSchema = z.object({
    versionNameGlobal: z.string(),
    versionNameMaintenance: z.string(),
    maintenanceKickMessage: z.string(),
    motdGlobal: motdSchema,
    motdMaintenance: motdSchema,
    tablistHeader: z.string(),
    tablistFooter: z.string(),
});

export const networkRestartConfirmationSchema = z.object({
    confirmationCode: z
        .string()
        .trim()
        .regex(/^\d{6}$/, "Enter the 6-digit confirmation code."),
});

export type NetworkSettingsApiSchema = z.infer<typeof networkSettingsApiSchema>;
export type NetworkServerSettingsFormValues = z.infer<typeof networkServerSettingsFormSchema>;
export type NetworkGeneralFormValues = z.infer<typeof networkGeneralFormSchema>;
export type NetworkMessagingFormValues = z.infer<typeof networkMessagingFormSchema>;
export type NetworkRestartConfirmationValues = z.infer<typeof networkRestartConfirmationSchema>;
