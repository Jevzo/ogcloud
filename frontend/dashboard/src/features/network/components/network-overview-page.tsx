import {
    ActivityIcon,
    AlertTriangleIcon,
    ServerIcon,
    ShieldCheckIcon,
    UsersIcon,
} from "lucide-react";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";

import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
    ChartContainer,
    ChartTooltip,
    ChartTooltipContent,
    type ChartConfig,
} from "@/components/ui/chart";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { useNetworkPageContext } from "@/pages/network/context";
import { formatProxyRoutingStrategy } from "@/pages/network/utils";

const capacityChartConfig = {
    online: {
        label: "Online players",
        color: "var(--primary)",
    },
    free: {
        label: "Free slots",
        color: "color-mix(in srgb, var(--color-primary) 22%, transparent)",
    },
} satisfies ChartConfig;

const OverviewStatCard = ({
    description,
    icon: Icon,
    isLoading,
    title,
    toneClassName,
    value,
}: {
    description: string;
    icon: typeof UsersIcon;
    isLoading: boolean;
    title: string;
    toneClassName?: string;
    value: string;
}) => (
    <Card className="border-border/70 bg-card/75">
        <CardHeader className="pb-3">
            <div className="flex items-start justify-between gap-3">
                <div>
                    <CardDescription>{title}</CardDescription>
                    <CardTitle className="mt-2 text-3xl tracking-tight text-foreground">
                        {isLoading ? <Skeleton className="h-8 w-24" /> : value}
                    </CardTitle>
                </div>
                <div
                    className={`rounded-xl border border-border/70 bg-background/70 p-2 ${toneClassName ?? ""}`}
                >
                    <Icon className="size-4" />
                </div>
            </div>
        </CardHeader>
        <CardContent className="pt-0 text-sm text-muted-foreground">{description}</CardContent>
    </Card>
);

const NetworkOverviewPage = () => {
    const { groups, isLoading, reloadData, settings, status } = useNetworkPageContext();
    const isUnavailable = !settings && !isLoading;

    if (isUnavailable) {
        return (
            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>Network overview unavailable</CardTitle>
                    <CardDescription>
                        The dashboard could not load network settings and status data for the
                        overview surface.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <Button type="button" variant="outline" onClick={() => void reloadData()}>
                        Retry loading overview
                    </Button>
                </CardContent>
            </Card>
        );
    }

    const maxPlayers = settings?.maxPlayers ?? 0;
    const remainingSlots = Math.max(maxPlayers - status.onlinePlayers, 0);
    const capacityPercent =
        maxPlayers > 0 ? Math.min(100, (status.onlinePlayers / maxPlayers) * 100) : 0;
    const activePlayersLabel = settings
        ? `${status.onlinePlayers.toLocaleString()} / ${maxPlayers.toLocaleString()}`
        : "--";
    const defaultGroupExists = settings
        ? groups.some((group) => group.id === settings.defaultGroup)
        : false;
    const capacityChartData = [
        {
            label: "Network slots",
            online: status.onlinePlayers,
            free: remainingSlots,
        },
    ];

    const healthTone = settings?.maintenance
        ? {
              badgeClassName: "border-amber-500/30 bg-amber-500/10 text-amber-300",
              description:
                  "Maintenance mode is enabled. Normal joins are restricted until it is disabled.",
              label: "Maintenance window",
          }
        : status.proxyCount === 0
          ? {
                badgeClassName: "border-destructive/30 bg-destructive/10 text-destructive",
                description:
                    "No proxies are currently registered. Players cannot reach the network edge.",
                label: "Critical",
            }
          : status.serverCount === 0
            ? {
                  badgeClassName: "border-amber-500/30 bg-amber-500/10 text-amber-300",
                  description:
                      "No game servers are registered. Proxies are available but gameplay cannot start.",
                  label: "Warning",
              }
            : {
                  badgeClassName: "border-emerald-500/30 bg-emerald-500/10 text-emerald-300",
                  description:
                      "Proxy, server, and player telemetry currently look healthy from the dashboard.",
                  label: "Healthy",
              };

    return (
        <div className="space-y-6">
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <OverviewStatCard
                    title="Online Players"
                    value={status.onlinePlayers.toLocaleString()}
                    description="Players currently connected across all proxies."
                    icon={UsersIcon}
                    isLoading={isLoading}
                    toneClassName="text-primary"
                />
                <OverviewStatCard
                    title="Capacity"
                    value={activePlayersLabel}
                    description="Current occupancy versus the configured network slot cap."
                    icon={ShieldCheckIcon}
                    isLoading={isLoading}
                    toneClassName="text-primary"
                />
                <OverviewStatCard
                    title="Game Servers"
                    value={status.serverCount.toLocaleString()}
                    description="Non-proxy runtime instances currently registered in the cluster."
                    icon={ServerIcon}
                    isLoading={isLoading}
                    toneClassName="text-primary"
                />
                <OverviewStatCard
                    title="Proxies"
                    value={status.proxyCount.toLocaleString()}
                    description="Gateway nodes currently routing traffic into the network."
                    icon={ActivityIcon}
                    isLoading={isLoading}
                    toneClassName="text-primary"
                />
            </div>

            <div className="grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)]">
                <div className="space-y-6">
                    <Card className="border-border/70 bg-card/80">
                        <CardHeader>
                            <div className="flex flex-wrap items-center gap-2">
                                <Badge variant="outline" className={healthTone.badgeClassName}>
                                    {healthTone.label}
                                </Badge>
                                {settings?.maintenance ? (
                                    <Badge variant="outline" className="border-border/80">
                                        Network-wide lockout active
                                    </Badge>
                                ) : null}
                            </div>
                            <CardTitle>Network health summary</CardTitle>
                            <CardDescription>{healthTone.description}</CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-5">
                            <div className="grid gap-3 md:grid-cols-2">
                                <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                    <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                        Default group
                                    </div>
                                    <div className="mt-2 flex items-center gap-2 text-sm font-semibold text-foreground">
                                        {isLoading ? (
                                            <Skeleton className="h-5 w-28" />
                                        ) : (
                                            <>
                                                <span>{settings?.defaultGroup ?? "--"}</span>
                                                {!defaultGroupExists && settings ? (
                                                    <Badge
                                                        variant="outline"
                                                        className="border-amber-500/30 bg-amber-500/10 text-amber-300"
                                                    >
                                                        Missing from group list
                                                    </Badge>
                                                ) : null}
                                            </>
                                        )}
                                    </div>
                                </div>
                                <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                    <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                        Proxy routing
                                    </div>
                                    <div className="mt-2 text-sm font-semibold text-foreground">
                                        {isLoading ? (
                                            <Skeleton className="h-5 w-36" />
                                        ) : (
                                            formatProxyRoutingStrategy(
                                                settings?.general.proxyRoutingStrategy ?? "LOAD_BASED",
                                            )
                                        )}
                                    </div>
                                </div>
                            </div>

                            <div className="space-y-3 rounded-xl border border-border/70 bg-background/55 p-4">
                                <div className="flex items-center justify-between gap-3">
                                    <div>
                                        <div className="text-sm font-semibold text-foreground">
                                            Player slot occupancy
                                        </div>
                                        <div className="text-sm text-muted-foreground">
                                            {settings
                                                ? `${status.onlinePlayers.toLocaleString()} players connected with ${remainingSlots.toLocaleString()} free slots.`
                                                : "Waiting for current network capacity."}
                                        </div>
                                    </div>
                                    <div className="text-right">
                                        <div className="text-lg font-semibold text-foreground">
                                            {Math.round(capacityPercent)}%
                                        </div>
                                        <div className="text-xs text-muted-foreground">occupied</div>
                                    </div>
                                </div>
                                <Progress value={capacityPercent} />
                            </div>

                            <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                <div className="mb-3 flex items-center justify-between gap-3">
                                    <div>
                                        <div className="text-sm font-semibold text-foreground">
                                            Connection capacity
                                        </div>
                                        <div className="text-sm text-muted-foreground">
                                            Live slot usage split between active players and remaining headroom.
                                        </div>
                                    </div>
                                    <Badge variant="outline" className="border-border/80">
                                        Live snapshot
                                    </Badge>
                                </div>
                                {isLoading ? (
                                    <Skeleton className="h-52 w-full rounded-xl" />
                                ) : (
                                    <ChartContainer
                                        config={capacityChartConfig}
                                        className="h-52 w-full aspect-auto"
                                    >
                                        <BarChart
                                            data={capacityChartData}
                                            layout="vertical"
                                            margin={{ left: 12, right: 12, top: 8, bottom: 8 }}
                                        >
                                            <CartesianGrid horizontal={false} />
                                            <XAxis type="number" hide />
                                            <YAxis
                                                type="category"
                                                dataKey="label"
                                                tickLine={false}
                                                axisLine={false}
                                                width={96}
                                            />
                                            <ChartTooltip
                                                cursor={false}
                                                content={<ChartTooltipContent indicator="dot" />}
                                            />
                                            <Bar
                                                dataKey="online"
                                                stackId="capacity"
                                                radius={[8, 0, 0, 8]}
                                                fill="var(--color-online)"
                                            />
                                            <Bar
                                                dataKey="free"
                                                stackId="capacity"
                                                radius={[0, 8, 8, 0]}
                                                fill="var(--color-free)"
                                            />
                                        </BarChart>
                                    </ChartContainer>
                                )}
                            </div>
                        </CardContent>
                    </Card>
                </div>

                <div className="space-y-6">
                    <Card className="border-border/70 bg-card/80">
                        <CardHeader>
                            <CardTitle>Plugin controls</CardTitle>
                            <CardDescription>
                                Fast confirmation that the major network-wide systems are aligned
                                with current operations.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="grid gap-3">
                            <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Permission system
                                </div>
                                <div className="mt-2">
                                    {isLoading ? (
                                        <Skeleton className="h-5 w-24" />
                                    ) : (
                                        <Badge
                                            variant="outline"
                                            className={
                                                settings?.general.permissionSystemEnabled
                                                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                    : "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                            }
                                        >
                                            {settings?.general.permissionSystemEnabled
                                                ? "Enabled"
                                                : "Disabled"}
                                        </Badge>
                                    )}
                                </div>
                            </div>
                            <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Tablist
                                </div>
                                <div className="mt-2">
                                    {isLoading ? (
                                        <Skeleton className="h-5 w-24" />
                                    ) : (
                                        <Badge
                                            variant="outline"
                                            className={
                                                settings?.general.tablistEnabled
                                                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                    : "border-border/80 text-muted-foreground"
                                            }
                                        >
                                            {settings?.general.tablistEnabled ? "Enabled" : "Disabled"}
                                        </Badge>
                                    )}
                                </div>
                            </div>
                            <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Maintenance
                                </div>
                                <div className="mt-2">
                                    {isLoading ? (
                                        <Skeleton className="h-5 w-24" />
                                    ) : (
                                        <Badge
                                            variant="outline"
                                            className={
                                                settings?.maintenance
                                                    ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                                    : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                            }
                                        >
                                            {settings?.maintenance ? "Enabled" : "Disabled"}
                                        </Badge>
                                    )}
                                </div>
                            </div>
                        </CardContent>
                    </Card>

                    <Card className="border-border/70 bg-card/80">
                        <CardHeader>
                            <CardTitle>Player-facing messaging</CardTitle>
                            <CardDescription>
                                Current network copy as players see it at the proxy edge.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            {isLoading ? (
                                <>
                                    <Skeleton className="h-24 w-full rounded-xl" />
                                    <Skeleton className="h-24 w-full rounded-xl" />
                                    <Skeleton className="h-24 w-full rounded-xl" />
                                </>
                            ) : (
                                <>
                                    <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                            Global MOTD
                                        </div>
                                        <MinecraftTextPreview
                                            value={settings?.motd.global}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                    <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                            Maintenance MOTD
                                        </div>
                                        <MinecraftTextPreview
                                            value={settings?.motd.maintenance}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                    <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                            Maintenance kick
                                        </div>
                                        <MinecraftTextPreview
                                            value={settings?.maintenanceKickMessage}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                </>
                            )}
                        </CardContent>
                    </Card>

                    {!defaultGroupExists && settings ? (
                        <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">
                            <div className="flex items-start gap-3">
                                <AlertTriangleIcon className="mt-0.5 size-4 shrink-0 text-amber-300" />
                                <div>
                                    <div className="font-medium">
                                        Default group is not in the current group list
                                    </div>
                                    <div className="mt-1 text-amber-100/85">
                                        Review the network default group in Server Settings before
                                        relying on automatic routing fallback.
                                    </div>
                                </div>
                            </div>
                        </div>
                    ) : null}
                </div>
            </div>
        </div>
    );
};

export default NetworkOverviewPage;
