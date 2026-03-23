import { Clock3Icon, LoaderCircleIcon, SearchIcon, UsersIcon } from "lucide-react";
import { useDeferredValue, useState } from "react";
import { useNavigate } from "react-router";

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
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { PageReveal, RevealGroup } from "@/components/ui/page-reveal";
import PlayerActionsMenu from "@/features/players/components/player-actions-menu";
import { usePersistedPlayersQuery } from "@/features/players/hooks/use-persisted-players-query";
import {
    formatPermissionExpiry,
    getPlayerStatusBadgeClassName,
} from "@/features/players/lib/player-display";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";

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

const PlayersTableSkeleton = () => (
    <div className="space-y-2 px-5 pb-5">
        {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={`players-table-skeleton-${index}`} className="h-12 w-full" />
        ))}
    </div>
);

const PlayersPage = () => {
    const navigate = useNavigate();
    const [currentPage, setCurrentPage] = useState(0);
    const [searchInput, setSearchInput] = useState("");

    const deferredQuery = useDeferredValue(searchInput.trim());
    const {
        data: playerPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
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
                <RevealGroup className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    {Array.from({ length: 4 }).map((_, index) => (
                        <Card
                            key={`players-summary-skeleton-${index}`}
                            className="border border-border/70 bg-card/85"
                        >
                            <CardHeader>
                                <Skeleton className="h-4 w-24" />
                                <Skeleton className="h-8 w-20" />
                            </CardHeader>
                            <CardContent>
                                <Skeleton className="h-4 w-40" />
                            </CardContent>
                        </Card>
                    ))}
                </RevealGroup>
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
        <PageReveal className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Player directory
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Persisted player profiles, current routing state, and direct access to each
                        player workspace.
                    </p>
                </div>

                <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
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

            <RevealGroup className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
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
            </RevealGroup>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-3 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Player inventory
                            </CardDescription>
                            <CardTitle className="text-base">Persisted player records</CardTitle>
                            <CardDescription>
                                Search by player name or UUID, then open the detail workspace for
                                routing and operational context.
                            </CardDescription>
                        </div>

                        <div className="w-full xl:max-w-[320px]">
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
                                    placeholder="Search player or UUID"
                                />
                            </InputGroup>
                        </div>
                    </div>
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
                                <TableHead className="px-4">UUID</TableHead>
                                <TableHead className="px-4 text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {playerPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={7}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No persisted players matched the current filter set.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                playerPage.items.map((player) => (
                                    <TableRow
                                        key={player.uuid}
                                        className="cursor-pointer"
                                        onClick={() =>
                                            navigate(`/players/${encodeURIComponent(player.uuid)}`)
                                        }
                                    >
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
                                                    {formatPermissionExpiry(
                                                        player.permission.endMillis,
                                                    )}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <Badge
                                                variant="outline"
                                                className={getPlayerStatusBadgeClassName(
                                                    player.online,
                                                )}
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
                                        <TableCell className="px-4 py-3 align-top font-mono text-xs text-muted-foreground">
                                            {player.uuid}
                                        </TableCell>
                                        <TableCell
                                            className="px-4 py-3 text-right align-top"
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            <PlayerActionsMenu
                                                onOpenPlayer={(nextPlayer) =>
                                                    navigate(
                                                        `/players/${encodeURIComponent(nextPlayer.uuid)}`,
                                                    )
                                                }
                                                player={player}
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
        </PageReveal>
    );
};

export default PlayersPage;
