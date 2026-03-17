import { z } from "zod";

export const dashboardOverviewStatsSchema = z.object({
    totalPlayers: z.number().int().nonnegative(),
    maxPlayers: z.number().int().nonnegative(),
    activeServers: z.number().int().nonnegative(),
    maintenanceEnabled: z.boolean(),
});

export const dashboardOverviewGroupSchema = z.object({
    name: z.string().min(1),
    mode: z.string().min(1),
    activeInstances: z.number().int().nonnegative(),
    players: z.number().int().nonnegative(),
    capacityPercent: z.number().finite().nonnegative(),
});

export const dashboardOverviewScalingActionSchema = z.object({
    id: z.string().min(1).nullable(),
    groupId: z.string().min(1),
    action: z.string().min(1),
    reason: z.string().min(1),
    serverId: z.string().min(1).nullable(),
    details: z.string().nullable(),
    timestamp: z.string().min(1),
});

const dashboardOverviewApiSchema = z.object({
    stats: dashboardOverviewStatsSchema,
    groups: z.array(dashboardOverviewGroupSchema),
    scalingActions: z.array(dashboardOverviewScalingActionSchema).nullable().optional(),
});

export const dashboardOverviewSchema = dashboardOverviewApiSchema.transform((payload) => ({
    ...payload,
    scalingActions: payload.scalingActions ?? [],
}));

export type DashboardOverviewStats = z.infer<typeof dashboardOverviewStatsSchema>;
export type DashboardOverviewGroup = z.infer<typeof dashboardOverviewGroupSchema>;
export type DashboardOverviewScalingAction = z.infer<
    typeof dashboardOverviewScalingActionSchema
>;
export type DashboardOverview = z.infer<typeof dashboardOverviewSchema>;

export const EMPTY_DASHBOARD_OVERVIEW: DashboardOverview = {
    stats: {
        totalPlayers: 0,
        maxPlayers: 0,
        activeServers: 0,
        maintenanceEnabled: false,
    },
    groups: [],
    scalingActions: [],
};
