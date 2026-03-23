import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import {
    ArrowLeftIcon,
    CopyIcon,
    ExternalLinkIcon,
    ServerIcon,
    TerminalIcon,
    UsersIcon,
} from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { PageReveal } from "@/components/ui/page-reveal";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import ExecuteCommandDialog from "@/features/servers/components/execute-command-dialog";
import ServerActionsMenu from "@/features/servers/components/server-actions-menu";
import ServerStatusBadge from "@/features/servers/components/server-status-badge";
import { useServerDetailsQuery } from "@/features/servers/hooks/use-server-details-query";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { getRuntimeProfileLabel } from "@/features/groups/lib/group-runtime";
import { hasAdminAccess } from "@/features/auth/lib/roles";
import {
    getServerActionSuccessMessage,
    runServerAction,
} from "@/features/servers/lib/server-actions";
import { formatDateTime, formatMemoryMb, formatTps } from "@/features/servers/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { ServerActionKind } from "@/types/server";

const StatCard = ({ helper, label, value }: { helper: string; label: string; value: string }) => (
    <Card className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="pb-3">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                {label}
            </CardDescription>
            <CardTitle className="text-xl tracking-tight">{value}</CardTitle>
        </CardHeader>
        <CardContent className="pt-0 text-sm text-muted-foreground">{helper}</CardContent>
    </Card>
);

const DetailRow = ({
    label,
    value,
    action,
    valueClassName,
}: {
    action?: React.ReactNode;
    label: string;
    value: string;
    valueClassName?: string;
}) => (
    <div className="flex flex-col gap-1 rounded-lg border border-border/70 bg-background/45 px-4 py-3">
        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">{label}</div>
        <div className="flex items-center gap-2">
            <div className={`min-w-0 text-sm font-medium text-foreground ${valueClassName ?? ""}`}>
                {value}
            </div>
            {action}
        </div>
    </div>
);

const ServerDetailsPage = () => {
    const params = useParams();
    const serverId = decodeURIComponent(params.serverId ?? "");
    const getAccessToken = useAccessToken();
    const session = useAuthStore((state) => state.session);
    const canExecuteCommands = hasAdminAccess(session?.user.role);

    const [activeActionKey, setActiveActionKey] = useState<string | null>(null);
    const [playerPageIndex, setPlayerPageIndex] = useState(0);
    const [isCommandDialogOpen, setIsCommandDialogOpen] = useState(false);

    const {
        errorMessage,
        group,
        isLoading,
        isRefreshing,
        playerPage,
        refresh,
        refreshIntervalMs,
        runtimeSnapshot,
        server,
    } = useServerDetailsQuery({
        playerPageIndex,
        serverId,
    });

    useEffect(() => {
        setPlayerPageIndex(0);
    }, [serverId]);

    const statsServer = runtimeSnapshot ?? server;
    const totalPlayerPages = getPaginatedTotalPages(playerPage);
    const isProxySurface = server?.type === "PROXY";
    const playerContextColumnLabel = isProxySurface ? "Server" : "Proxy";

    const handleServerAction = async (nextServerId: string, action: ServerActionKind) => {
        const actionKey = `${nextServerId}:${action}`;
        setActiveActionKey(actionKey);

        try {
            const accessToken = await getAccessToken();
            await runServerAction(accessToken, nextServerId, action);
            toast.success(getServerActionSuccessMessage(nextServerId, action));
            await refresh(false);
        } catch (error) {
            toast.error(
                error instanceof Error
                    ? error.message
                    : "Unable to execute the selected server action.",
            );
        } finally {
            setActiveActionKey(null);
        }
    };

    const handleCopyServerId = async (value: string) => {
        try {
            await navigator.clipboard.writeText(value);
            toast.success("Server ID copied.");
        } catch {
            toast.error("Unable to copy the server ID.");
        }
    };

    if (isLoading && !server) {
        return null;
    }

    if (errorMessage && !server) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Server Error
                    </CardDescription>
                    <CardTitle className="text-destructive">Unable to load this server</CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
                <CardFooter className="justify-between gap-3">
                    <Button variant="outline" asChild>
                        <Link to="/servers">
                            <ArrowLeftIcon className="size-4" />
                            Back to servers
                        </Link>
                    </Button>
                </CardFooter>
            </Card>
        );
    }

    return (
        <PageReveal className="space-y-4">
            <div className="space-y-4">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                    <Button variant="ghost" size="sm" asChild className="-ml-2 w-fit">
                        <Link to="/servers">
                            <ArrowLeftIcon className="size-4" />
                            Back to servers
                        </Link>
                    </Button>

                    <div className="flex flex-wrap items-center gap-2 xl:justify-end">
                        {server && group ? (
                            <Button variant="outline" asChild>
                                <Link to={`/groups/${encodeURIComponent(group.id)}`}>
                                    <ExternalLinkIcon className="size-4" />
                                    Open group config
                                </Link>
                            </Button>
                        ) : null}
                        {server && canExecuteCommands ? (
                            <Button variant="outline" onClick={() => setIsCommandDialogOpen(true)}>
                                <TerminalIcon className="size-4" />
                                Execute command
                            </Button>
                        ) : null}
                        {server ? (
                            <ServerActionsMenu
                                activeActionKey={activeActionKey}
                                canExecuteCommands={false}
                                onAction={handleServerAction}
                                server={server}
                            />
                        ) : null}
                    </div>
                </div>

                <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                        {server ? <ServerStatusBadge state={server.state} /> : null}
                        {server ? (
                            <Badge variant="outline" className="border-border/80">
                                {server.type}
                            </Badge>
                        ) : null}
                    </div>

                    <div>
                        <CardTitle className="flex items-center gap-2 text-2xl tracking-tight">
                            <ServerIcon className="size-5 text-primary" />
                            {server?.displayName ?? serverId}
                        </CardTitle>
                        <CardDescription className="mt-2 max-w-3xl text-sm leading-6">
                            Runtime telemetry, lifecycle actions, and player sessions for the
                            selected server instance.
                        </CardDescription>
                    </div>
                </div>
            </div>

            {errorMessage && server ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful server snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
                <StatCard
                    label="Group"
                    value={server?.group ?? "--"}
                    helper={
                        group
                            ? `Backend runtime: ${getRuntimeProfileLabel(group.runtimeProfile)}`
                            : "Backend runtime unavailable"
                    }
                />
                <StatCard
                    label="Players"
                    value={
                        statsServer
                            ? `${statsServer.playerCount} / ${statsServer.maxPlayers}`
                            : "--"
                    }
                    helper="Connected sessions and maximum player capacity."
                />
                <StatCard
                    label="TPS"
                    value={statsServer ? formatTps(statsServer.tps) : "--"}
                    helper="Latest runtime tick signal from the heartbeat snapshot."
                />
                <StatCard
                    label="Memory"
                    value={statsServer ? formatMemoryMb(statsServer.memoryUsedMb) : "--"}
                    helper="Heap usage reported by the active runtime."
                />
                <StatCard
                    label="Heartbeat"
                    value={statsServer ? formatDateTime(statsServer.lastHeartbeat) : "--"}
                    helper="Most recent successful heartbeat observed by the dashboard."
                />
            </div>

            <Tabs defaultValue="overview" className="gap-4">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                    <TabsList variant="line">
                        <TabsTrigger value="overview">Overview</TabsTrigger>
                        <TabsTrigger value="players">
                            Players
                            <Badge variant="outline" className="ml-1 border-border/80">
                                {playerPage.totalItems}
                            </Badge>
                        </TabsTrigger>
                    </TabsList>
                    <Badge variant="outline" className="w-fit border-border/80">
                        Runtime stats refresh in place every {Math.round(refreshIntervalMs / 1000)}s
                    </Badge>
                </div>

                <TabsContent value="overview" className="space-y-4">
                    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader className="pb-4">
                                <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                    Identity
                                </CardDescription>
                                <CardTitle className="text-base">Server metadata</CardTitle>
                            </CardHeader>
                            <CardContent className="grid gap-3 md:grid-cols-2">
                                <DetailRow
                                    label="Server ID"
                                    value={server?.id ?? "--"}
                                    valueClassName="truncate whitespace-nowrap"
                                    action={
                                        server?.id ? (
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="icon-xs"
                                                className="shrink-0"
                                                onClick={() => void handleCopyServerId(server.id)}
                                                aria-label="Copy server ID"
                                                title="Copy server ID"
                                            >
                                                <CopyIcon className="size-4" />
                                            </Button>
                                        ) : null
                                    }
                                />
                                <DetailRow
                                    label="Display name"
                                    value={server?.displayName ?? "--"}
                                />
                                <DetailRow label="Type" value={server?.type ?? "--"} />
                                <DetailRow label="Game state" value={server?.gameState ?? "--"} />
                            </CardContent>
                        </Card>

                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader className="pb-4">
                                <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                    Runtime
                                </CardDescription>
                                <CardTitle className="text-base">
                                    Pod, template, and network context
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="grid gap-3 md:grid-cols-2">
                                <DetailRow label="Pod IP" value={server?.podIp ?? "--"} />
                                <DetailRow label="Port" value={server ? `${server.port}` : "--"} />
                                <DetailRow
                                    label="Template version"
                                    value={server?.templateVersion ?? "--"}
                                />
                                <DetailRow
                                    label="Started at"
                                    value={server ? formatDateTime(server.startedAt) : "--"}
                                />
                            </CardContent>
                        </Card>
                    </div>
                </TabsContent>

                <TabsContent value="players">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader className="border-b border-border/70 pb-4">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                <div>
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Connected players
                                    </CardDescription>
                                    <CardTitle className="flex items-center gap-2 text-base">
                                        <UsersIcon className="size-4 text-primary" />
                                        Player sessions on this instance
                                    </CardTitle>
                                    <CardDescription>
                                        Live sessions currently attached to this server or proxy.
                                    </CardDescription>
                                </div>
                            </div>
                        </CardHeader>
                        <CardContent className="px-0">
                            <Table>
                                <TableHeader>
                                    <TableRow className="hover:bg-transparent">
                                        <TableHead>Player</TableHead>
                                        <TableHead>Permission group</TableHead>
                                        <TableHead>{playerContextColumnLabel}</TableHead>
                                        <TableHead>Connected</TableHead>
                                        <TableHead>UUID</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {playerPage.items.length === 0 ? (
                                        <TableRow>
                                            <TableCell
                                                colSpan={5}
                                                className="py-12 text-center text-sm text-muted-foreground"
                                            >
                                                No players are currently connected to this instance.
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        playerPage.items.map((player) => (
                                            <TableRow key={player.uuid}>
                                                <TableCell className="align-top font-medium text-foreground">
                                                    {player.name}
                                                </TableCell>
                                                <TableCell className="align-top text-muted-foreground">
                                                    {player.groupId ?? "--"}
                                                </TableCell>
                                                <TableCell className="align-top text-muted-foreground">
                                                    {isProxySurface
                                                        ? player.serverDisplayName ||
                                                          player.serverId ||
                                                          "--"
                                                        : player.proxyDisplayName ||
                                                          player.proxyId ||
                                                          "--"}
                                                </TableCell>
                                                <TableCell className="align-top text-muted-foreground">
                                                    {formatDateTime(player.connectedAt)}
                                                </TableCell>
                                                <TableCell className="align-top font-mono text-xs text-muted-foreground">
                                                    {player.uuid}
                                                </TableCell>
                                            </TableRow>
                                        ))
                                    )}
                                </TableBody>
                            </Table>
                        </CardContent>
                        <CardFooter className="justify-between gap-3">
                            <div className="text-sm text-muted-foreground">
                                Page {playerPage.page + 1} of {totalPlayerPages}
                            </div>
                            <div className="flex items-center gap-2">
                                <Button
                                    variant="outline"
                                    onClick={() =>
                                        setPlayerPageIndex((value) => Math.max(0, value - 1))
                                    }
                                    disabled={playerPageIndex === 0 || isRefreshing}
                                >
                                    Previous
                                </Button>
                                <Button
                                    variant="outline"
                                    onClick={() => setPlayerPageIndex((value) => value + 1)}
                                    disabled={!getPaginatedHasNext(playerPage) || isRefreshing}
                                >
                                    Next
                                </Button>
                            </div>
                        </CardFooter>
                    </Card>
                </TabsContent>
            </Tabs>

            {server && canExecuteCommands ? (
                <ExecuteCommandDialog
                    description="Send a console command directly to this server instance."
                    open={isCommandDialogOpen}
                    submitLabel="Send to server"
                    target={server.id}
                    targetType="server"
                    title={`Execute command for ${server.displayName}`}
                    onOpenChange={setIsCommandDialogOpen}
                />
            ) : null}
        </PageReveal>
    );
};

export default ServerDetailsPage;
