import { Link } from "react-router";
import {
    AlertTriangleIcon,
    ArrowRightIcon,
    Clock3Icon,
    ServerIcon,
    ShieldAlertIcon,
    ShieldCheckIcon,
    UsersIcon,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardAction,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { useDashboardOverviewQuery } from "@/features/dashboard/hooks/use-dashboard-overview-query";
import type {
    DashboardOverviewGroup,
    DashboardOverviewScalingAction,
} from "@/features/dashboard/schemas";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { cn } from "@/lib/utils";

const formatPercent = (value: number) => `${Math.round(Math.min(100, value))}%`;

const getGroupModeBadgeClassName = (mode: string) =>
    mode.toUpperCase() === "STATIC"
        ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
        : "border-primary/30 bg-primary/10 text-primary";

const getScalingActionBadgeClassName = (action: string) => {
    const normalizedAction = action.toUpperCase();

    if (
        normalizedAction.includes("UP") ||
        normalizedAction.includes("START") ||
        normalizedAction.includes("CREATE")
    ) {
        return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
    }

    if (
        normalizedAction.includes("DOWN") ||
        normalizedAction.includes("STOP") ||
        normalizedAction.includes("DELETE")
    ) {
        return "border-amber-500/30 bg-amber-500/10 text-amber-300";
    }

    return "border-border/80 bg-muted/60 text-foreground";
};

interface MetricCardProps {
    description: string;
    icon: typeof UsersIcon;
    iconToneClassName: string;
    isLoading: boolean;
    meta: string;
    metaToneClassName: string;
    title: string;
    value: string;
}

const MetricCard = ({
    description,
    icon: Icon,
    iconToneClassName,
    isLoading,
    meta,
    metaToneClassName,
    title,
    value,
}: MetricCardProps) => (
    <Card className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="pb-3">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                {title}
            </CardDescription>
            <CardAction>
                <div
                    className={cn(
                        "flex size-10 items-center justify-center rounded-lg border",
                        iconToneClassName,
                    )}
                >
                    <Icon className="size-[18px]" />
                </div>
            </CardAction>
            <CardTitle className="text-2xl tracking-tight">
                {isLoading ? <Skeleton className="h-8 w-28" /> : value}
            </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
            {isLoading ? (
                <>
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-2/3" />
                </>
            ) : (
                <>
                    <p className={cn("text-sm font-medium", metaToneClassName)}>{meta}</p>
                    <p className="text-sm text-muted-foreground">{description}</p>
                </>
            )}
        </CardContent>
    </Card>
);

const GroupCard = ({ group }: { group: DashboardOverviewGroup }) => {
    const playersPerInstance =
        group.activeInstances > 0 ? `${Math.round(group.players / group.activeInstances)}` : "--";

    return (
        <Link
            to={`/groups/${encodeURIComponent(group.name)}`}
            className="block rounded-lg outline-none ring-offset-2 transition-transform focus-visible:ring-2 focus-visible:ring-ring/60"
        >
            <Card className="h-full border border-border/70 bg-background/55 shadow-none transition-colors hover:border-primary/30 hover:bg-background/70">
                <CardHeader className="pb-3">
                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                        Network group
                    </CardDescription>
                    <CardAction>
                        <Badge variant="outline" className={getGroupModeBadgeClassName(group.mode)}>
                            {group.mode}
                        </Badge>
                    </CardAction>
                    <CardTitle className="flex min-w-0 items-center gap-2 text-base">
                        <span className="truncate">{group.name}</span>
                        <ArrowRightIcon className="mt-px size-4 shrink-0 text-muted-foreground" />
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="grid grid-cols-2 gap-3">
                        <div className="rounded-lg border border-border/70 bg-muted/25 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Players
                            </div>
                            <div className="mt-1 text-lg font-semibold text-foreground">
                                {group.players}
                            </div>
                        </div>
                        <div className="rounded-lg border border-border/70 bg-muted/25 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Instances
                            </div>
                            <div className="mt-1 text-lg font-semibold text-foreground">
                                {group.activeInstances}
                            </div>
                        </div>
                    </div>

                    <div className="space-y-2">
                        <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
                            <span>Capacity pressure</span>
                            <span className="font-medium text-foreground">
                                {formatPercent(group.capacityPercent)}
                            </span>
                        </div>
                        <Progress value={Math.min(100, group.capacityPercent)} className="h-2.5" />
                    </div>

                    <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
                        <span>Players per instance</span>
                        <span className="font-medium text-foreground">{playersPerInstance}</span>
                    </div>
                </CardContent>
            </Card>
        </Link>
    );
};

const LastSyncSurface = ({
    isRefreshing,
    lastUpdatedAt,
}: {
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
}) => (
    <div className="flex min-h-10 items-center gap-2 rounded-lg border border-border/70 bg-card/70 px-3 text-sm text-muted-foreground">
        <Clock3Icon className="size-4 text-primary" />
        <span>
            {lastUpdatedAt
                ? `Last sync ${formatDateTime(new Date(lastUpdatedAt).toISOString())}`
                : "Waiting for first sync"}
        </span>
        {isRefreshing ? <span className="text-primary">Syncing...</span> : null}
    </div>
);

const ScalingActionsTable = ({
    scalingActions,
}: {
    scalingActions: DashboardOverviewScalingAction[];
}) => (
    <Card className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="border-b border-border/70 pb-4">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                Recent Scaling
            </CardDescription>
            <CardTitle className="text-base">Latest autoscaling actions</CardTitle>
            <CardDescription>
                Most recent controller decisions across the visible network groups.
            </CardDescription>
        </CardHeader>
        <CardContent className="px-0">
            <Table>
                <TableHeader>
                    <TableRow className="hover:bg-transparent">
                        <TableHead>Group</TableHead>
                        <TableHead>Action</TableHead>
                        <TableHead>Reason</TableHead>
                        <TableHead>Server</TableHead>
                        <TableHead>Timestamp</TableHead>
                    </TableRow>
                </TableHeader>
                <TableBody>
                    {scalingActions.length === 0 ? (
                        <TableRow>
                            <TableCell
                                colSpan={5}
                                className="py-10 text-center text-sm text-muted-foreground"
                            >
                                No scaling actions have been recorded yet.
                            </TableCell>
                        </TableRow>
                    ) : (
                        scalingActions.map((action) => (
                            <TableRow
                                key={
                                    action.id ??
                                    `${action.groupId}:${action.action}:${action.timestamp}`
                                }
                            >
                                <TableCell className="align-top">
                                    <Link
                                        to={`/groups/${encodeURIComponent(action.groupId)}`}
                                        className="font-medium text-foreground hover:text-primary"
                                    >
                                        {action.groupId}
                                    </Link>
                                </TableCell>
                                <TableCell className="align-top">
                                    <Badge
                                        variant="outline"
                                        className={getScalingActionBadgeClassName(action.action)}
                                    >
                                        {action.action}
                                    </Badge>
                                </TableCell>
                                <TableCell className="align-top">
                                    <div className="max-w-md space-y-1 whitespace-normal">
                                        <div className="font-medium text-foreground">
                                            {action.reason}
                                        </div>
                                        {action.details ? (
                                            <div className="text-xs text-muted-foreground">
                                                {action.details}
                                            </div>
                                        ) : null}
                                    </div>
                                </TableCell>
                                <TableCell className="align-top text-muted-foreground">
                                    {action.serverId ?? "--"}
                                </TableCell>
                                <TableCell className="align-top text-muted-foreground">
                                    {formatDateTime(action.timestamp)}
                                </TableCell>
                            </TableRow>
                        ))
                    )}
                </TableBody>
            </Table>
        </CardContent>
    </Card>
);

const DashboardHomeSkeleton = () => (
    <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card
                    key={`metric-skeleton-${index}`}
                    className="border border-border/70 bg-card/85"
                >
                    <CardHeader>
                        <Skeleton className="h-4 w-24" />
                        <Skeleton className="h-8 w-32" />
                    </CardHeader>
                    <CardContent className="space-y-2">
                        <Skeleton className="h-4 w-full" />
                        <Skeleton className="h-4 w-2/3" />
                    </CardContent>
                </Card>
            ))}
        </div>

        <Card className="border border-border/70 bg-card/85">
            <CardHeader>
                <Skeleton className="h-4 w-28" />
                <Skeleton className="h-7 w-44" />
            </CardHeader>
            <CardContent className="grid gap-4 xl:grid-cols-3">
                {Array.from({ length: 3 }).map((_, index) => (
                    <div
                        key={`group-skeleton-${index}`}
                        className="space-y-3 rounded-lg border border-border/70 bg-background/40 p-5"
                    >
                        <Skeleton className="h-4 w-24" />
                        <Skeleton className="h-6 w-32" />
                        <Skeleton className="h-20 w-full" />
                        <Skeleton className="h-2.5 w-full" />
                    </div>
                ))}
            </CardContent>
        </Card>

        <Card className="border border-border/70 bg-card/85">
            <CardHeader>
                <Skeleton className="h-4 w-28" />
                <Skeleton className="h-7 w-48" />
            </CardHeader>
            <CardContent className="space-y-2">
                {Array.from({ length: 5 }).map((_, index) => (
                    <Skeleton key={`table-skeleton-${index}`} className="h-11 w-full" />
                ))}
            </CardContent>
        </Card>
    </div>
);

const DashboardHomePage = () => {
    const {
        averageLatencyMs,
        averageLatencyWindowMs,
        data,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
    } = useDashboardOverviewQuery();

    const playerLoadPercent =
        data.stats.maxPlayers > 0
            ? Math.min(100, Math.round((data.stats.totalPlayers / data.stats.maxPlayers) * 100))
            : 0;
    const hasFreshData = lastUpdatedAt !== null;

    const metrics = [
        {
            title: "Total Players",
            value:
                data.stats.maxPlayers > 0
                    ? `${data.stats.totalPlayers} / ${data.stats.maxPlayers}`
                    : `${data.stats.totalPlayers}`,
            meta: `${playerLoadPercent}% of player capacity`,
            description: "Current network load across connected sessions and available slots.",
            iconToneClassName: "border-emerald-500/20 bg-emerald-500/10 text-emerald-300",
            metaToneClassName: "text-emerald-300",
            icon: UsersIcon,
        },
        {
            title: "Active Servers",
            value: `${data.stats.activeServers}`,
            meta: "Visible running instances",
            description: "Runtime instances currently registered and considered active.",
            iconToneClassName: "border-primary/20 bg-primary/10 text-primary",
            metaToneClassName: "text-primary",
            icon: ServerIcon,
        },
        {
            title: "API Latency",
            value: averageLatencyMs !== null ? `${averageLatencyMs} ms` : "--",
            meta: `${Math.round(averageLatencyWindowMs / 60000)} minute rolling average`,
            description: "Observed dashboard edge latency from authenticated API requests.",
            iconToneClassName: "border-sky-500/20 bg-sky-500/10 text-sky-300",
            metaToneClassName: "text-sky-300",
            icon: Clock3Icon,
        },
        {
            title: "Maintenance Mode",
            value: data.stats.maintenanceEnabled ? "Enabled" : "Disabled",
            meta: data.stats.maintenanceEnabled
                ? "Player joins are currently restricted"
                : "New joins are being accepted",
            description: "Network-wide entry control sourced from the active settings document.",
            iconToneClassName: data.stats.maintenanceEnabled
                ? "border-amber-500/20 bg-amber-500/10 text-amber-300"
                : "border-emerald-500/20 bg-emerald-500/10 text-emerald-300",
            metaToneClassName: data.stats.maintenanceEnabled
                ? "text-amber-300"
                : "text-emerald-300",
            icon: data.stats.maintenanceEnabled ? ShieldAlertIcon : ShieldCheckIcon,
        },
    ] as const;

    if (isLoading && !hasFreshData) {
        return <DashboardHomeSkeleton />;
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Dashboard Error
                    </CardDescription>
                    <CardTitle className="flex items-center gap-2 text-destructive">
                        <AlertTriangleIcon className="size-5" />
                        Unable to load the operational overview
                    </CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <div className="text-sm text-muted-foreground">
                        The dashboard will keep retrying automatically.
                    </div>
                </CardContent>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Operational snapshot
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Live network capacity, group pressure, and recent autoscaling decisions in
                        one surface.
                    </p>
                </div>

                <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
            </div>

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="flex items-center gap-2 text-base text-amber-200">
                            <AlertTriangleIcon className="size-4" />
                            Showing the latest successful dashboard snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                {metrics.map((metric) => (
                    <MetricCard key={metric.title} {...metric} isLoading={false} />
                ))}
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="pb-4">
                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                        Group Pressure
                    </CardDescription>
                    <CardAction>
                        <Button variant="ghost" size="sm" asChild>
                            <Link to="/groups">
                                View groups
                                <ArrowRightIcon className="size-4" />
                            </Link>
                        </Button>
                    </CardAction>
                    <CardTitle className="text-base">Most active network groups</CardTitle>
                    <CardDescription>
                        Dense runtime cards prioritized by current player activity and visible
                        instance pressure.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    {data.groups.length === 0 ? (
                        <div className="rounded-lg border border-dashed border-border/80 bg-background/35 px-4 py-10 text-center text-sm text-muted-foreground">
                            No visible groups are active right now.
                        </div>
                    ) : (
                        <div className="grid gap-4 xl:grid-cols-3">
                            {data.groups.map((group) => (
                                <GroupCard key={group.name} group={group} />
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            <ScalingActionsTable scalingActions={data.scalingActions} />
        </div>
    );
};

export default DashboardHomePage;
