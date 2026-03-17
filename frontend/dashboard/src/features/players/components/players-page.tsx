import { SearchIcon, UsersIcon, PencilLineIcon } from "lucide-react";
import { useDeferredValue, useState } from "react";

import PlayerManagementModal from "@/components/PlayerManagementModal";
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
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
} from "@/components/ui/input-group";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { usePersistedPlayersQuery } from "@/features/players/hooks/use-persisted-players-query";
import { formatDateTime } from "@/lib/server-display";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { PersistedPlayerRecord } from "@/types/player";

const getPlayerStatusBadgeClassName = (online: boolean) =>
    online
        ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
        : "border-border/80 text-muted-foreground";

const formatPermissionExpiry = (endMillis: number) => {
    if (endMillis === -1) {
        return "Permanent";
    }

    if (!Number.isFinite(endMillis) || endMillis <= 0) {
        return "--";
    }

    return formatDateTime(String(endMillis));
};

const SummaryCard = ({
    helper,
    label,
    value,
}: {
    helper: string;
    label: string;
    value: string;
}) => (
    <Card size="sm" className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="pb-3">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                {label}
            </CardDescription>
            <CardTitle className="text-xl tracking-tight">{value}</CardTitle>
        </CardHeader>
        <CardContent className="pt-0 text-sm text-muted-foreground">{helper}</CardContent>
    </Card>
);

const PlayersTableSkeleton = () => (
    <div className="space-y-2 px-4 pb-4">
        {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={`players-table-skeleton-${index}`} className="h-12 w-full" />
        ))}
    </div>
);

const PlayersPage = () => {
    const [currentPage, setCurrentPage] = useState(0);
    const [searchInput, setSearchInput] = useState("");
    const [selectedPlayer, setSelectedPlayer] = useState<PersistedPlayerRecord | null>(null);

    const deferredQuery = useDeferredValue(searchInput.trim());
    const {
        data: playerPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
        refreshIntervalMs,
    } = usePersistedPlayersQuery({
        currentPage,
        query: deferredQuery,
    });

    const totalPages = getPaginatedTotalPages(playerPage);
    const onlinePlayers = playerPage.items.filter((player) => player.online).length;
    const temporaryGrants = playerPage.items.filter(
        (player) => Number.isFinite(player.permission.endMillis) && player.permission.endMillis > 0,
    ).length;
    const visiblePermissionGroups = new Set(
        playerPage.items.map((player) => player.permission.group),
    ).size;
    const hasFreshData = lastUpdatedAt !== null;

    if (isLoading && !hasFreshData) {
        return (
            <div className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    {Array.from({ length: 4 }).map((_, index) => (
                        <Card key={`players-summary-skeleton-${index}`} className="border border-border/70 bg-card/85">
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
                        <Skeleton className="h-8 w-40" />
                        <Skeleton className="h-4 w-64" />
                    </CardHeader>
                    <CardContent className="space-y-4">
                        <Skeleton className="h-8 w-full" />
                    </CardContent>
                    <PlayersTableSkeleton />
                </Card>
            </div>
        );
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Players Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load persisted players
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
                <div className="space-y-2">
                    <Badge variant="outline" className="w-fit border-primary/25 bg-primary/10 text-primary">
                        Player management
                    </Badge>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            Players
                        </h1>
                        <p className="max-w-3xl text-sm text-muted-foreground">
                            Review persisted player records, inspect current routing state, and
                            launch permission or transfer actions without leaving the table.
                        </p>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="outline" className="border-border/80">
                        Refreshes every {Math.round(refreshIntervalMs / 1000)}s
                    </Badge>
                    {lastUpdatedAt ? (
                        <Badge variant="outline" className="border-border/80">
                            Last sync {formatDateTime(new Date(lastUpdatedAt).toISOString())}
                        </Badge>
                    ) : null}
                </div>
            </div>

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful player snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Persisted records"
                    value={`${playerPage.totalItems}`}
                    helper="Total persisted player records visible to the dashboard."
                />
                <SummaryCard
                    label="Online on page"
                    value={`${onlinePlayers}`}
                    helper="Players currently connected among the rendered rows."
                />
                <SummaryCard
                    label="Permission groups"
                    value={`${visiblePermissionGroups}`}
                    helper="Distinct permission groups represented on the current page."
                />
                <SummaryCard
                    label="Temporary grants"
                    value={`${temporaryGrants}`}
                    helper="Players with a non-permanent permission assignment on this page."
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Player inventory
                            </CardDescription>
                            <CardTitle className="text-base">Persisted player records</CardTitle>
                            <CardDescription>
                                Search by player name or UUID, then launch the management surface
                                for live routing and permission changes.
                            </CardDescription>
                        </div>
                    </div>

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
                            placeholder="Search player name or UUID"
                        />
                    </InputGroup>
                </CardHeader>

                <CardContent className="px-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-transparent">
                                <TableHead className="px-4">Player</TableHead>
                                <TableHead className="px-4">Permission</TableHead>
                                <TableHead className="px-4">Status</TableHead>
                                <TableHead className="px-4">First join</TableHead>
                                <TableHead className="px-4">Connected</TableHead>
                                <TableHead className="px-4">Route</TableHead>
                                <TableHead className="px-4">UUID</TableHead>
                                <TableHead className="px-4 text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {playerPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={8}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No persisted players matched the current filter set.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                playerPage.items.map((player) => (
                                    <TableRow key={player.uuid}>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="flex items-center gap-2 font-medium text-foreground">
                                                    <UsersIcon className="size-4 text-primary" />
                                                    {player.name}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {player.online
                                                        ? "Eligible for live actions"
                                                        : "Offline persisted record"}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {player.permission.group}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {formatPermissionExpiry(player.permission.endMillis)}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <Badge
                                                variant="outline"
                                                className={getPlayerStatusBadgeClassName(player.online)}
                                            >
                                                {player.online ? "Online" : "Offline"}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                            {formatDateTime(player.firstJoin)}
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                            {formatDateTime(player.connectedAt)}
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1 text-xs text-muted-foreground">
                                                <div>Proxy: {player.proxyId ?? "--"}</div>
                                                <div>Server: {player.serverId ?? "--"}</div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top font-mono text-xs text-muted-foreground">
                                            {player.uuid}
                                        </TableCell>
                                        <TableCell className="px-4 py-3 text-right align-top">
                                            <Button
                                                variant="outline"
                                                size="icon-sm"
                                                onClick={() => setSelectedPlayer(player)}
                                                aria-label={`Manage ${player.name}`}
                                            >
                                                <PencilLineIcon className="size-4" />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </CardContent>

                <CardFooter className="justify-between gap-3">
                    <div className="text-sm text-muted-foreground">
                        Page {playerPage.page + 1} of {totalPages}
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
                            disabled={!getPaginatedHasNext(playerPage) || isRefreshing}
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>

            <PlayerManagementModal
                player={selectedPlayer}
                onClose={() => setSelectedPlayer(null)}
                onPlayerUpdated={() => {
                    void refresh(false);
                }}
                onTransferComplete={() => refresh(false)}
            />
        </div>
    );
};

export default PlayersPage;
