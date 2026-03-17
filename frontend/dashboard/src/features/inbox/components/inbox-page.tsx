import { LoaderCircleIcon, SearchIcon, ShieldAlertIcon } from "lucide-react";
import { useState } from "react";

import { Badge } from "@/components/ui/badge";
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
import { Skeleton } from "@/components/ui/skeleton";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { useAuditLogQuery } from "@/features/inbox/hooks/use-audit-log-query";
import { hasAdminAccess } from "@/lib/roles";
import { formatDateTime } from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import type { ApiAuditLogRecord } from "@/types/audit";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";

const getActionBadgeClassName = (action: string) => {
    const normalizedAction = action.trim().toUpperCase();

    if (
        normalizedAction.includes("DELETE") ||
        normalizedAction.includes("REMOVE") ||
        normalizedAction.includes("REVOKE")
    ) {
        return "border-red-500/30 bg-red-500/10 text-red-300";
    }

    if (
        normalizedAction.includes("CREATE") ||
        normalizedAction.includes("ADD") ||
        normalizedAction.includes("LINK")
    ) {
        return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
    }

    return "border-primary/25 bg-primary/10 text-primary";
};

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

const InboxPageSkeleton = () => (
    <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
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
        </div>
        <Card className="border border-border/70 bg-card/85">
            <CardHeader>
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-8 w-56" />
                <Skeleton className="h-4 w-72" />
            </CardHeader>
            <CardContent className="space-y-4">
                <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
                    <Skeleton className="h-8 w-full" />
                    <Skeleton className="h-8 w-24" />
                </div>
            </CardContent>
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
    const [query, setQuery] = useState("");

    const {
        data: auditPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    } = useAuditLogQuery({
        currentPage,
        enabled: canReviewAuditLogs,
        query: query.trim(),
    });

    const totalPages = getPaginatedTotalPages(auditPage);
    const hasFreshData = lastUpdatedAt !== null;
    const visibleActions = new Set(auditPage.items.map((entry) => entry.action)).size;
    const systemEvents = auditPage.items.filter(
        (entry) => !entry.actorEmail && !entry.actorUserId,
    ).length;
    const metadataEntries = auditPage.items.filter(
        (entry) => Object.keys(entry.metadata).length > 0,
    ).length;

    if (!canReviewAuditLogs) {
        return (
            <div className="space-y-4">
                <div className="space-y-2">
                    <Badge
                        variant="outline"
                        className="w-fit border-primary/25 bg-primary/10 text-primary"
                    >
                        Audit trail
                    </Badge>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            Inbox
                        </h1>
                        <p className="max-w-3xl text-sm text-muted-foreground">
                            Review API-level audit activity and operator actions recorded by the
                            control plane.
                        </p>
                    </div>
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
            </div>
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
                <CardFooter>
                    <Button onClick={() => void refresh(true)}>Retry</Button>
                </CardFooter>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                    <Badge
                        variant="outline"
                        className="w-fit border-primary/25 bg-primary/10 text-primary"
                    >
                        Audit trail
                    </Badge>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            Inbox
                        </h1>
                        <p className="max-w-3xl text-sm text-muted-foreground">
                            Review control-plane API activity, search recent actor and target
                            combinations, and verify which actions reached the cluster edge.
                        </p>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    {query.trim() ? (
                        <Badge variant="outline" className="border-border/80">
                            Filtered
                        </Badge>
                    ) : null}
                    {lastUpdatedAt ? (
                        <Badge variant="outline" className="border-border/80">
                            Last sync {formatDateTime(new Date(lastUpdatedAt).toISOString())}
                        </Badge>
                    ) : null}
                    <Button
                        variant="outline"
                        onClick={() => void refresh()}
                        disabled={isRefreshing}
                    >
                        {isRefreshing ? (
                            <LoaderCircleIcon className="size-4 animate-spin" />
                        ) : null}
                        Refresh
                    </Button>
                </div>
            </div>

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful audit snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Visible entries"
                    value={`${auditPage.totalItems}`}
                    helper="Total audit log entries visible to the current query."
                />
                <SummaryCard
                    label="Actions on page"
                    value={`${visibleActions}`}
                    helper="Distinct audit action names represented in the current table view."
                />
                <SummaryCard
                    label="System events"
                    value={`${systemEvents}`}
                    helper="Entries that were emitted without an attributed dashboard actor."
                />
                <SummaryCard
                    label="Metadata rich"
                    value={`${metadataEntries}`}
                    helper="Rows on this page that include metadata for quick operator context."
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                API audit log
                            </CardDescription>
                            <CardTitle className="text-base">Recorded operator activity</CardTitle>
                            <CardDescription>
                                Search by action, actor, or summary text, then inspect target,
                                metadata, and timing information in one dense table.
                            </CardDescription>
                        </div>
                        <CardAction>
                            <Badge variant="outline" className="border-border/80">
                                Page {auditPage.page + 1} of {totalPages}
                            </Badge>
                        </CardAction>
                    </div>

                    <InputGroup>
                        <InputGroupAddon>
                            <SearchIcon className="size-4" />
                        </InputGroupAddon>
                        <InputGroupInput
                            value={query}
                            onChange={(event) => {
                                setQuery(event.target.value);
                                setCurrentPage(0);
                            }}
                            placeholder="Search action, actor, summary, or target"
                        />
                    </InputGroup>
                </CardHeader>

                <CardContent className="px-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-transparent">
                                <TableHead className="px-4">Action</TableHead>
                                <TableHead className="px-4">Target</TableHead>
                                <TableHead className="px-4">Summary</TableHead>
                                <TableHead className="px-4">Actor</TableHead>
                                <TableHead className="px-4">Time</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {auditPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={5}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No API audit log entries matched the current filter set.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                auditPage.items.map((entry) => {
                                    const metadataPreview = formatMetadataPreview(entry);

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
                                                    className={getActionBadgeClassName(entry.action)}
                                                >
                                                    {entry.action}
                                                </Badge>
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top whitespace-normal">
                                                <div className="space-y-1">
                                                    <div className="font-medium text-foreground">
                                                        {entry.targetId}
                                                    </div>
                                                    <div className="text-xs text-muted-foreground">
                                                        {entry.targetType}
                                                    </div>
                                                </div>
                                            </TableCell>
                                            <TableCell className="max-w-[26rem] px-4 py-3 align-top whitespace-normal">
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
                                            <TableCell className="px-4 py-3 align-top whitespace-normal">
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
        </div>
    );
};

export default InboxPage;
