import { useDeferredValue, useState } from "react";
import { useNavigate } from "react-router";
import {
    Clock3Icon,
    FilterIcon,
    LoaderCircleIcon,
    SearchIcon,
    ServerIcon,
    TerminalIcon,
} from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
    Card,
    CardAction,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
} from "@/components/ui/input-group";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import DeployServerDialog from "@/features/servers/components/deploy-server-dialog";
import ExecuteCommandDialog from "@/features/servers/components/execute-command-dialog";
import ServerActionsMenu from "@/features/servers/components/server-actions-menu";
import ServerStatusBadge from "@/features/servers/components/server-status-badge";
import { useServerGroupsQuery } from "@/features/servers/hooks/use-server-groups-query";
import { useServersQuery } from "@/features/servers/hooks/use-servers-query";
import { useAccessToken } from "@/hooks/use-access-token";
import { getRuntimeProfileLabel } from "@/lib/group-runtime";
import { hasAdminAccess } from "@/lib/roles";
import { getServerActionSuccessMessage, runServerAction } from "@/lib/server-actions";
import { formatDateTime, formatMemoryMb, formatTps } from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { CommandTargetType } from "@/types/command";
import type { ServerActionKind, ServerRecord } from "@/types/server";

interface CommandDialogState {
    description: string;
    submitLabel: string;
    target: string;
    targetType: CommandTargetType;
    title: string;
}

const SummaryCard = ({
    helper,
    label,
    value,
}: {
    helper: string;
    label: string;
    value: string;
}) => (
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

const LastSyncSurface = ({
    isRefreshing,
    lastUpdatedAt,
}: {
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
}) => (
    <div className="flex min-h-10 items-center gap-2 rounded-lg border border-border/70 bg-card/70 px-3 text-sm text-muted-foreground">
        {isRefreshing ? (
            <LoaderCircleIcon className="size-4 animate-spin text-primary" />
        ) : (
            <Clock3Icon className="size-4 text-primary" />
        )}
        <span>
            {lastUpdatedAt
                ? `Last sync ${formatDateTime(new Date(lastUpdatedAt).toISOString())}`
                : "Waiting for first sync"}
        </span>
    </div>
);

const ServersTableSkeleton = () => (
    <div className="space-y-2 px-5 pb-5">
        {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={`servers-table-skeleton-${index}`} className="h-12 w-full" />
        ))}
    </div>
);

const getRuntimeLabel = (server: ServerRecord, runtimeProfileByGroupId: Map<string, string>) => {
    const runtimeLabel = runtimeProfileByGroupId.get(server.group);

    if (runtimeLabel) {
        return runtimeLabel;
    }

    return server.type === "PROXY" ? "Managed Velocity" : "--";
};

const ServersPage = () => {
    const navigate = useNavigate();
    const getAccessToken = useAccessToken();
    const session = useAuthStore((state) => state.session);
    const canExecuteCommands = hasAdminAccess(session?.user.role);

    const [currentPage, setCurrentPage] = useState(0);
    const [groupFilter, setGroupFilter] = useState("all");
    const [searchInput, setSearchInput] = useState("");
    const [activeActionKey, setActiveActionKey] = useState<string | null>(null);
    const [isDeployDialogOpen, setIsDeployDialogOpen] = useState(false);
    const [commandDialogState, setCommandDialogState] = useState<CommandDialogState | null>(null);

    const deferredQuery = useDeferredValue(searchInput.trim());
    const normalizedGroupFilter = groupFilter === "all" ? "" : groupFilter;
    const {
        data: groups,
        errorMessage: groupsErrorMessage,
        isLoading: isLoadingGroups,
    } = useServerGroupsQuery();
    const {
        data: serverPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    } = useServersQuery({
        currentPage,
        groupFilter: normalizedGroupFilter,
        query: deferredQuery,
    });

    const runtimeProfileByGroupId = new Map(
        groups.map((group) => [group.id, getRuntimeProfileLabel(group.runtimeProfile)] as const),
    );
    const totalPages = getPaginatedTotalPages(serverPage);
    const runningCount = serverPage.items.filter((server) => server.state.toUpperCase() === "RUNNING").length;
    const onlinePlayers = serverPage.items.reduce((total, server) => total + server.playerCount, 0);
    const visibleGroups = new Set(serverPage.items.map((server) => server.group)).size;
    const hasFreshData = lastUpdatedAt !== null;

    const handleServerAction = async (serverId: string, action: ServerActionKind) => {
        const actionKey = `${serverId}:${action}`;
        setActiveActionKey(actionKey);

        try {
            const accessToken = await getAccessToken();
            await runServerAction(accessToken, serverId, action);
            toast.success(getServerActionSuccessMessage(serverId, action));
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

    const openServerCommandDialog = (server: ServerRecord) => {
        setCommandDialogState({
            description: "Send a console command directly to this server instance.",
            submitLabel: "Send to server",
            target: server.id,
            targetType: "server",
            title: `Execute command for ${server.displayName}`,
        });
    };

    const openToolbarCommandDialog = () => {
        if (normalizedGroupFilter) {
            setCommandDialogState({
                description: `Send a console command to every running server in ${normalizedGroupFilter}.`,
                submitLabel: "Send to group",
                target: normalizedGroupFilter,
                targetType: "group",
                title: `Execute command for ${normalizedGroupFilter}`,
            });
            return;
        }

        setCommandDialogState({
            description: "Send a console command to every running server across the network.",
            submitLabel: "Send to all servers",
            target: "all",
            targetType: "all",
            title: "Execute network command",
        });
    };

    if (isLoading && !hasFreshData) {
        return (
            <div className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    {Array.from({ length: 4 }).map((_, index) => (
                        <Card key={`servers-summary-skeleton-${index}`} className="border border-border/70 bg-card/85">
                            <CardHeader>
                                <Skeleton className="h-4 w-24" />
                                <Skeleton className="h-8 w-20" />
                            </CardHeader>
                            <CardContent>
                                <Skeleton className="h-4 w-40" />
                            </CardContent>
                        </Card>
                    ))}
                </div>
                <Card className="border border-border/70 bg-card/85">
                    <CardHeader>
                        <Skeleton className="h-4 w-20" />
                        <Skeleton className="h-8 w-48" />
                        <Skeleton className="h-4 w-64" />
                    </CardHeader>
                    <CardContent className="space-y-4">
                        <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_240px]">
                            <Skeleton className="h-10 w-full" />
                            <Skeleton className="h-10 w-full" />
                        </div>
                    </CardContent>
                    <ServersTableSkeleton />
                </Card>
            </div>
        );
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Servers Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load the server inventory
                    </CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Runtime inventory
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Inspect live instances, filter by group, and execute runtime actions from
                        one operational table.
                    </p>
                </div>

                <div className="flex flex-col gap-3 sm:flex-row sm:items-center lg:justify-end">
                    <Button
                        onClick={() => setIsDeployDialogOpen(true)}
                        disabled={isLoadingGroups && groups.length === 0}
                    >
                        <ServerIcon className="size-4" />
                        Deploy new
                    </Button>
                    <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
                </div>
            </div>

            {errorMessage && hasFreshData ? (
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

            {groupsErrorMessage ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Group metadata is partially unavailable
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {groupsErrorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Total instances"
                    value={`${serverPage.totalItems}`}
                    helper="Backend paginated server inventory currently visible to this dashboard."
                />
                <SummaryCard
                    label="Running on page"
                    value={`${runningCount}`}
                    helper="Instances in RUNNING state on the currently loaded page."
                />
                <SummaryCard
                    label="Players on page"
                    value={`${onlinePlayers}`}
                    helper="Sum of current connected players across the rendered server rows."
                />
                <SummaryCard
                    label="Visible groups"
                    value={`${visibleGroups}`}
                    helper={
                        normalizedGroupFilter
                            ? `Filtered to ${normalizedGroupFilter}.`
                            : "Distinct groups represented on the current page."
                    }
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Server inventory
                            </CardDescription>
                            <CardTitle className="text-base">Live runtime instances</CardTitle>
                            <CardDescription>
                                Search by server, group, state, or pod metadata. Open row actions
                                for direct lifecycle control.
                            </CardDescription>
                        </div>
                        {canExecuteCommands ? (
                            <CardAction className="col-auto row-auto">
                                <Button variant="outline" onClick={openToolbarCommandDialog}>
                                    <TerminalIcon className="size-4" />
                                    {normalizedGroupFilter ? "Command group" : "Command all"}
                                </Button>
                            </CardAction>
                        ) : null}
                    </div>

                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_240px]">
                        <InputGroup>
                            <InputGroupAddon>
                                <SearchIcon className="size-4" />
                            </InputGroupAddon>
                            <InputGroupInput
                                value={searchInput}
                                onChange={(event) => {
                                    setSearchInput(event.target.value);
                                    setCurrentPage(0);
                                }}
                                placeholder="Search servers, groups, or pod"
                            />
                        </InputGroup>

                        <Select
                            value={groupFilter}
                            onValueChange={(value) => {
                                setGroupFilter(value);
                                setCurrentPage(0);
                            }}
                            disabled={isLoadingGroups}
                        >
                            <SelectTrigger className="w-full">
                                <FilterIcon className="size-4 text-muted-foreground" />
                                <SelectValue placeholder="Filter by group" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="all">All groups</SelectItem>
                                {groups.map((group) => (
                                    <SelectItem key={group.id} value={group.id}>
                                        {group.id}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                </CardHeader>

                <CardContent className="px-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-transparent">
                                <TableHead>Server</TableHead>
                                <TableHead>Group / Template</TableHead>
                                <TableHead>Runtime</TableHead>
                                <TableHead>State</TableHead>
                                <TableHead>TPS</TableHead>
                                <TableHead>Players</TableHead>
                                <TableHead>Memory</TableHead>
                                <TableHead>Heartbeat</TableHead>
                                <TableHead className="text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {serverPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={9}
                                        className="py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No servers matched the current filter set.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                serverPage.items.map((server) => (
                                    <TableRow
                                        key={server.id}
                                        className="cursor-pointer"
                                        onClick={() =>
                                            navigate(`/servers/${encodeURIComponent(server.id)}`)
                                        }
                                    >
                                        <TableCell className="align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {server.displayName}
                                                </div>
                                                <div className="font-mono text-xs text-muted-foreground">
                                                    {server.id}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {server.group}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {server.templateVersion}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {getRuntimeLabel(server, runtimeProfileByGroupId)}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {server.type}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="align-top">
                                            <ServerStatusBadge state={server.state} />
                                        </TableCell>
                                        <TableCell className="align-top">
                                            <span
                                                className={
                                                    server.tps >= 18
                                                        ? "font-medium text-emerald-300"
                                                        : "font-medium text-amber-300"
                                                }
                                            >
                                                {formatTps(server.tps)}
                                            </span>
                                        </TableCell>
                                        <TableCell className="align-top text-muted-foreground">
                                            {server.playerCount} / {server.maxPlayers}
                                        </TableCell>
                                        <TableCell className="align-top text-muted-foreground">
                                            {formatMemoryMb(server.memoryUsedMb)}
                                        </TableCell>
                                        <TableCell className="align-top text-muted-foreground">
                                            {formatDateTime(server.lastHeartbeat)}
                                        </TableCell>
                                        <TableCell
                                            className="text-right align-top"
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            <ServerActionsMenu
                                                activeActionKey={activeActionKey}
                                                canExecuteCommands={canExecuteCommands}
                                                onAction={handleServerAction}
                                                onExecuteCommand={openServerCommandDialog}
                                                onOpenServer={(nextServer) =>
                                                    navigate(
                                                        `/servers/${encodeURIComponent(nextServer.id)}`,
                                                    )
                                                }
                                                server={server}
                                            />
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </CardContent>

                <CardFooter className="justify-between gap-3">
                    <div className="text-sm text-muted-foreground">
                        Page {serverPage.page + 1} of {totalPages}
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            onClick={() => setCurrentPage((value) => Math.max(0, value - 1))}
                            disabled={currentPage === 0 || isRefreshing}
                        >
                            Previous
                        </Button>
                        <Button
                            variant="outline"
                            onClick={() => setCurrentPage((value) => value + 1)}
                            disabled={!getPaginatedHasNext(serverPage) || isRefreshing}
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>

            <DeployServerDialog
                groups={groups}
                open={isDeployDialogOpen}
                onCompleted={() => refresh(false)}
                onOpenChange={setIsDeployDialogOpen}
            />

            {commandDialogState ? (
                <ExecuteCommandDialog
                    description={commandDialogState.description}
                    open={commandDialogState !== null}
                    submitLabel={commandDialogState.submitLabel}
                    target={commandDialogState.target}
                    targetType={commandDialogState.targetType}
                    title={commandDialogState.title}
                    onOpenChange={(isOpen) => {
                        if (!isOpen) {
                            setCommandDialogState(null);
                        }
                    }}
                />
            ) : null}
        </div>
    );
};

export default ServersPage;
