import {
    Clock3Icon,
    FilterIcon,
    LoaderCircleIcon,
    SearchIcon,
    ShieldAlertIcon,
} from "lucide-react";
import { useDeferredValue, useState } from "react";

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
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { PageReveal, RevealGroup } from "@/components/ui/page-reveal";
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { hasAdminAccess } from "@/features/auth/lib/roles";
import { useAuditLogQuery } from "@/features/inbox/hooks/use-audit-log-query";
import {
    formatAuditActionLabel,
    getActionBadgeClassName,
    getAvailableAuditActions,
} from "@/features/inbox/lib/audit-display";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import type { ApiAuditLogRecord } from "@/types/audit";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";

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

const formatMetadataValue = (value: unknown): string => {
    if (value === null) {
        return "null";
    }

    if (typeof value === "string") {
        return value;
    }

    if (typeof value === "number" || typeof value === "boolean" || typeof value === "bigint") {
        return String(value);
    }

    try {
        return JSON.stringify(value);
    } catch {
        return String(value);
    }
};

const formatMetadataPreview = (entry: ApiAuditLogRecord) => {
    const metadataEntries = Object.entries(entry.metadata);

    if (metadataEntries.length === 0) {
        return null;
    }

    return metadataEntries
        .slice(0, 2)
        .map(([key, value]) => `${key}: ${formatMetadataValue(value)}`)
        .join(" | ");
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

const InboxPageSkeleton = () => (
    <div className="space-y-4">
        <RevealGroup className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card
                    key={`audit-summary-skeleton-${index}`}
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
            <CardHeader className="gap-4">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-8 w-56" />
                <Skeleton className="h-4 w-72" />
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
                    <Skeleton className="h-10 w-full lg:w-56" />
                    <Skeleton className="h-10 w-full lg:w-80" />
                </div>
            </CardHeader>
            <div className="space-y-2 px-4 pb-4">
                {Array.from({ length: 8 }).map((_, index) => (
                    <Skeleton key={`audit-table-skeleton-${index}`} className="h-12 w-full" />
                ))}
            </div>
        </Card>
    </div>
);

const InboxPage = () => {
    const session = useAuthStore((state) => state.session);
    const canReviewAuditLogs = hasAdminAccess(session?.user.role);

    const [currentPage, setCurrentPage] = useState(0);
    const [actionFilter, setActionFilter] = useState("all");
    const [searchInput, setSearchInput] = useState("");
    const deferredSearchQuery = useDeferredValue(searchInput.trim());
    const effectiveQuery = actionFilter === "all" ? deferredSearchQuery : actionFilter;

    const {
        data: auditPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
    } = useAuditLogQuery({
        currentPage,
        enabled: canReviewAuditLogs,
        query: effectiveQuery,
    });

    const totalPages = getPaginatedTotalPages(auditPage);
    const hasFreshData = lastUpdatedAt !== null;
    const availableActions = getAvailableAuditActions(auditPage.items.map((entry) => entry.action));
    const rowsOnPage = auditPage.items.length;
    const visibleActions = new Set(auditPage.items.map((entry) => entry.action)).size;
    const systemEvents = auditPage.items.filter(
        (entry) => !entry.actorEmail && !entry.actorUserId,
    ).length;

    if (!canReviewAuditLogs) {
        return (
            <PageReveal className="space-y-4">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Audit inbox
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Operator actions, actor context, and control-plane changes captured in the
                        audit log.
                    </p>
                </div>

                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-amber-200">
                            <ShieldAlertIcon className="size-4" />
                            Restricted
                        </CardTitle>
                        <CardDescription className="text-amber-100/80">
                            API audit logs are only available to admin and service accounts.
                        </CardDescription>
                    </CardHeader>
                </Card>
            </PageReveal>
        );
    }

    if (isLoading && !hasFreshData) {
        return <InboxPageSkeleton />;
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Inbox Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load audit activity
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
                        Audit inbox
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Operator actions, actor context, and control-plane changes captured in the
                        audit log.
                    </p>
                </div>

                <div className="flex flex-col gap-3 sm:flex-row sm:items-center lg:justify-end">
                    <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
                </div>
            </div>

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful inbox snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <RevealGroup className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Total entries"
                    value={`${auditPage.totalItems}`}
                    helper={
                        actionFilter === "all"
                            ? "Backend total for the current search query."
                            : `Backend total for ${formatAuditActionLabel(actionFilter)}.`
                    }
                />
                <SummaryCard
                    label="Rows on page"
                    value={`${rowsOnPage}`}
                    helper={
                        actionFilter === "all"
                            ? "Rows currently rendered from the loaded page."
                            : `Rows returned for ${formatAuditActionLabel(actionFilter)} on this page.`
                    }
                />
                <SummaryCard
                    label="System events"
                    value={`${systemEvents}`}
                    helper="Rendered rows without an attributed dashboard actor."
                />
                <SummaryCard
                    label="Actions on page"
                    value={`${visibleActions}`}
                    helper="Distinct audit action names represented in the rendered rows."
                />
            </RevealGroup>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                API audit log
                            </CardDescription>
                            <CardTitle className="text-base">Recorded operator activity</CardTitle>
                            <CardDescription>
                                Search by action, actor, summary, or target text. The action filter
                                uses the backend query parameter directly.
                            </CardDescription>
                        </div>

                        <div className="flex w-full flex-col gap-3 xl:w-auto xl:flex-row xl:items-center">
                            <div className="w-full xl:w-[240px]">
                                <Select
                                    value={actionFilter}
                                    onValueChange={(value) => {
                                        setActionFilter(value);
                                        setSearchInput("");
                                        setCurrentPage(0);
                                    }}
                                >
                                    <SelectTrigger className="w-full">
                                        <FilterIcon className="size-4 text-muted-foreground" />
                                        <SelectValue placeholder="Filter by action" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="all">All actions</SelectItem>
                                        {availableActions.map((action) => (
                                            <SelectItem key={action} value={action}>
                                                {formatAuditActionLabel(action)}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>

                            <div className="w-full xl:w-[320px]">
                                <InputGroup>
                                    <InputGroupAddon>
                                        <SearchIcon className="size-4" />
                                    </InputGroupAddon>
                                    <InputGroupInput
                                        className="text-[13px] placeholder:text-[13px]"
                                        disabled={actionFilter !== "all"}
                                        value={searchInput}
                                        onChange={(event) => {
                                            setSearchInput(event.target.value);
                                            setCurrentPage(0);
                                        }}
                                        placeholder={
                                            actionFilter === "all"
                                                ? "Search action, actor, summary, or target"
                                                : "Clear action filter to search other fields"
                                        }
                                    />
                                </InputGroup>
                            </div>
                        </div>
                    </div>
                </CardHeader>

                <CardContent className="px-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-transparent">
                                <TableHead className="px-4 w-[18%]">Action</TableHead>
                                <TableHead className="px-4 w-[48%]">Summary</TableHead>
                                <TableHead className="px-4 w-[24%]">Actor</TableHead>
                                <TableHead className="px-4">Time</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {auditPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={4}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No API audit log entries matched the current query.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                auditPage.items.map((entry) => {
                                    const metadataPreview = formatMetadataPreview(entry);
                                    const actionLabel = formatAuditActionLabel(entry.action);

                                    return (
                                        <TableRow
                                            key={
                                                entry.id ??
                                                `${entry.action}:${entry.timestamp}:${entry.targetId}`
                                            }
                                        >
                                            <TableCell className="px-4 py-3 align-top">
                                                <Badge
                                                    variant="outline"
                                                    className={getActionBadgeClassName(
                                                        entry.action,
                                                    )}
                                                >
                                                    {actionLabel}
                                                </Badge>
                                            </TableCell>
                                            <TableCell className="min-w-[28rem] max-w-[42rem] px-4 py-3 align-top whitespace-normal">
                                                <div className="space-y-1">
                                                    <div className="text-sm text-foreground">
                                                        {entry.summary}
                                                    </div>
                                                    {metadataPreview ? (
                                                        <div className="truncate text-xs text-muted-foreground">
                                                            {metadataPreview}
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </TableCell>
                                            <TableCell className="min-w-[16rem] px-4 py-3 align-top whitespace-normal">
                                                <div className="space-y-1">
                                                    <div className="font-medium text-foreground">
                                                        {entry.actorEmail ||
                                                            entry.actorUserId ||
                                                            "System"}
                                                    </div>
                                                    {entry.actorEmail && entry.actorUserId ? (
                                                        <div className="text-xs text-muted-foreground">
                                                            {entry.actorUserId}
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                                {formatDateTime(entry.timestamp)}
                                            </TableCell>
                                        </TableRow>
                                    );
                                })
                            )}
                        </TableBody>
                    </Table>
                </CardContent>

                <CardFooter className="justify-between gap-3">
                    <div className="text-sm text-muted-foreground">
                        Page {auditPage.page + 1} of {totalPages}
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
                            disabled={!getPaginatedHasNext(auditPage) || isRefreshing}
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>
        </PageReveal>
    );
};

export default InboxPage;
