import { EllipsisIcon, FileArchiveIcon, LoaderCircleIcon, SearchIcon, UploadIcon } from "lucide-react";
import { useDeferredValue, useMemo, useState } from "react";
import { toast } from "sonner";

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
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
} from "@/components/ui/input-group";
import { Skeleton } from "@/components/ui/skeleton";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import UploadTemplateDialog from "@/features/templates/components/upload-template-dialog";
import { useTemplateMetadataQuery } from "@/features/templates/hooks/use-template-metadata-query";
import { useTemplatesQuery } from "@/features/templates/hooks/use-templates-query";
import { useAccessToken } from "@/hooks/use-access-token";
import { deleteTemplate, downloadTemplate, uploadTemplate } from "@/lib/api";
import { formatDateTime } from "@/lib/server-display";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { TemplateRecord } from "@/types/template";

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

const TemplatesTableSkeleton = () => (
    <div className="space-y-2 px-4 pb-4">
        {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={`templates-table-skeleton-${index}`} className="h-12 w-full" />
        ))}
    </div>
);

const TemplatesPage = () => {
    const getAccessToken = useAccessToken();
    const [currentPage, setCurrentPage] = useState(0);
    const [groupFilter, setGroupFilter] = useState("all");
    const [searchInput, setSearchInput] = useState("");
    const [isUploadDialogOpen, setIsUploadDialogOpen] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [activeActionKey, setActiveActionKey] = useState<string | null>(null);
    const [deleteTarget, setDeleteTarget] = useState<TemplateRecord | null>(null);

    const deferredQuery = useDeferredValue(searchInput.trim());
    const normalizedGroupFilter = groupFilter === "all" ? "" : groupFilter;
    const {
        data: templatePage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
        refreshIntervalMs,
    } = useTemplatesQuery({
        currentPage,
        groupFilter: normalizedGroupFilter,
        query: deferredQuery,
    });
    const {
        data: templateMetadata,
        errorMessage: metadataErrorMessage,
    } = useTemplateMetadataQuery();

    const templateUsageCountByKey = useMemo(() => {
        const usageCounts = new Map<string, number>();

        templateMetadata.serverGroups.forEach((group) => {
            const key = `${group.templatePath}::${group.templateVersion}`;
            usageCounts.set(key, (usageCounts.get(key) ?? 0) + 1);
        });

        return usageCounts;
    }, [templateMetadata.serverGroups]);

    const totalPages = getPaginatedTotalPages(templatePage);
    const hasFreshData = lastUpdatedAt !== null;
    const visibleNamespaces = new Set(templatePage.items.map((template) => template.group)).size;
    const referencedVersions = templatePage.items.filter(
        (template) =>
            (templateUsageCountByKey.get(`${template.group}::${template.version}`) ?? 0) > 0,
    ).length;
    const activeReferences = templatePage.items.reduce(
        (total, template) =>
            total + (templateUsageCountByKey.get(`${template.group}::${template.version}`) ?? 0),
        0,
    );

    const handleUpload = async (values: { file: File; group: string; version: string }) => {
        setIsUploading(true);

        try {
            const accessToken = await getAccessToken();
            await uploadTemplate(
                accessToken,
                values.group.trim(),
                values.version.trim(),
                values.file,
            );

            toast.success(`Uploaded ${values.group.trim()} / ${values.version.trim()}.`);
            setIsUploadDialogOpen(false);

            if (currentPage !== 0) {
                setCurrentPage(0);
            } else {
                await refresh(false);
            }
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to upload template.");
        } finally {
            setIsUploading(false);
        }
    };

    const handleDownload = async (template: TemplateRecord) => {
        const actionKey = `${template.group}:${template.version}:download`;
        setActiveActionKey(actionKey);

        try {
            const accessToken = await getAccessToken();
            await downloadTemplate(accessToken, template.group, template.version);
            toast.success(`Downloaded ${template.group} / ${template.version}.`);
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to download template.");
        } finally {
            setActiveActionKey(null);
        }
    };

    const handleDelete = async () => {
        if (!deleteTarget) {
            return;
        }

        const actionKey = `${deleteTarget.group}:${deleteTarget.version}:delete`;
        setActiveActionKey(actionKey);

        try {
            const accessToken = await getAccessToken();
            await deleteTemplate(accessToken, deleteTarget.group, deleteTarget.version);
            toast.success(`Deleted ${deleteTarget.group} / ${deleteTarget.version}.`);
            setDeleteTarget(null);

            if (templatePage.items.length === 1 && currentPage > 0) {
                setCurrentPage((value) => Math.max(0, value - 1));
            } else {
                await refresh(false);
            }
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to delete template.");
        } finally {
            setActiveActionKey(null);
        }
    };

    if (isLoading && !hasFreshData) {
        return (
            <div className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    {Array.from({ length: 4 }).map((_, index) => (
                        <Card key={`templates-summary-skeleton-${index}`} className="border border-border/70 bg-card/85">
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
                        <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px_auto]">
                            <Skeleton className="h-8 w-full" />
                            <Skeleton className="h-8 w-full" />
                            <Skeleton className="h-8 w-full" />
                        </div>
                    </CardContent>
                    <TemplatesTableSkeleton />
                </Card>
            </div>
        );
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Templates Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load template archives
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
                        Template catalog
                    </Badge>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            Templates
                        </h1>
                        <p className="max-w-3xl text-sm text-muted-foreground">
                            Review stored template archives, publish new versions, and keep the
                            group-to-template mapping visible from one operational catalog.
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
                            Showing the latest successful template snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            {metadataErrorMessage ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Group metadata is partially unavailable
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {metadataErrorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Template archives"
                    value={`${templatePage.totalItems}`}
                    helper="Total stored template rows visible to the current dashboard query."
                />
                <SummaryCard
                    label="Namespaces"
                    value={`${visibleNamespaces}`}
                    helper="Distinct template group namespaces represented on this page."
                />
                <SummaryCard
                    label="Referenced versions"
                    value={`${referencedVersions}`}
                    helper="Visible template versions currently referenced by one or more groups."
                />
                <SummaryCard
                    label="Active references"
                    value={`${activeReferences}`}
                    helper="Total group configurations pointing at the template rows on this page."
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Template inventory
                            </CardDescription>
                            <CardTitle className="text-base">Stored template archives</CardTitle>
                            <CardDescription>
                                Filter by group or path metadata, then download or retire specific
                                template versions from the action menu.
                            </CardDescription>
                        </div>
                        <CardAction>
                            <div className="flex flex-wrap items-center gap-2">
                                <Button onClick={() => setIsUploadDialogOpen(true)}>
                                    <UploadIcon className="size-4" />
                                    Upload template
                                </Button>
                            </div>
                        </CardAction>
                    </div>

                    <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_220px]">
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
                                placeholder="Search group, version, or template path"
                            />
                        </InputGroup>

                        <Select
                            value={groupFilter}
                            onValueChange={(value) => {
                                setGroupFilter(value);
                                setCurrentPage(0);
                            }}
                        >
                            <SelectTrigger className="w-full">
                                <SelectValue placeholder="Filter by group" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="all">All groups</SelectItem>
                                {templateMetadata.groups.map((group) => (
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
                                <TableHead className="px-4">Group</TableHead>
                                <TableHead className="px-4">Version</TableHead>
                                <TableHead className="px-4">Path</TableHead>
                                <TableHead className="px-4">Usage</TableHead>
                                <TableHead className="px-4 text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {templatePage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={5}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No templates matched the current filter set.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                templatePage.items.map((template) => {
                                    const usageCount =
                                        templateUsageCountByKey.get(
                                            `${template.group}::${template.version}`,
                                        ) ?? 0;
                                    const isBusy = activeActionKey !== null;

                                    return (
                                        <TableRow key={`${template.group}:${template.version}:${template.path}`}>
                                            <TableCell className="px-4 py-3 align-top">
                                                <Badge
                                                    variant="outline"
                                                    className="border-primary/25 bg-primary/10 text-primary"
                                                >
                                                    {template.group}
                                                </Badge>
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top font-mono text-sm text-foreground">
                                                {template.version}
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top font-mono text-xs text-muted-foreground">
                                                {template.path}
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top">
                                                {usageCount > 0 ? (
                                                    <Badge
                                                        variant="outline"
                                                        className="border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                    >
                                                        {usageCount} group{usageCount === 1 ? "" : "s"}
                                                    </Badge>
                                                ) : (
                                                    <Badge variant="outline" className="border-border/80">
                                                        Unused
                                                    </Badge>
                                                )}
                                            </TableCell>
                                            <TableCell className="px-4 py-3 text-right align-top">
                                                <DropdownMenu>
                                                    <DropdownMenuTrigger asChild>
                                                        <Button
                                                            variant="outline"
                                                            size="icon-sm"
                                                            disabled={isBusy}
                                                            aria-label={`Open actions for ${template.group} ${template.version}`}
                                                        >
                                                            {activeActionKey?.startsWith(
                                                                `${template.group}:${template.version}`,
                                                            ) ? (
                                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                                            ) : (
                                                                <EllipsisIcon className="size-4" />
                                                            )}
                                                        </Button>
                                                    </DropdownMenuTrigger>
                                                    <DropdownMenuContent align="end" className="min-w-44">
                                                        <DropdownMenuItem
                                                            onSelect={(event) => {
                                                                event.preventDefault();
                                                                void handleDownload(template);
                                                            }}
                                                        >
                                                            <FileArchiveIcon className="size-4" />
                                                            Download archive
                                                        </DropdownMenuItem>
                                                        <DropdownMenuItem
                                                            variant="destructive"
                                                            onSelect={(event) => {
                                                                event.preventDefault();
                                                                setDeleteTarget(template);
                                                            }}
                                                        >
                                                            Delete template
                                                        </DropdownMenuItem>
                                                    </DropdownMenuContent>
                                                </DropdownMenu>
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
                        Page {templatePage.page + 1} of {totalPages}
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
                            disabled={!getPaginatedHasNext(templatePage) || isRefreshing}
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>

            <UploadTemplateDialog
                groupSuggestions={templateMetadata.groups.map((group) => group.id)}
                isSubmitting={isUploading}
                open={isUploadDialogOpen}
                onOpenChange={setIsUploadDialogOpen}
                onSubmit={handleUpload}
            />

            <AlertDialog
                open={deleteTarget !== null}
                onOpenChange={(open) => {
                    if (!open) {
                        setDeleteTarget(null);
                    }
                }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Delete template</AlertDialogTitle>
                        <AlertDialogDescription>
                            {deleteTarget
                                ? `Delete ${deleteTarget.group} / ${deleteTarget.version}? This removes the stored archive and cannot be undone from the dashboard.`
                                : "Delete the selected template."}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={activeActionKey !== null}>
                            Cancel
                        </AlertDialogCancel>
                        <AlertDialogAction
                            variant="destructive"
                            disabled={activeActionKey !== null}
                            onClick={(event) => {
                                event.preventDefault();
                                void handleDelete();
                            }}
                        >
                            {activeActionKey?.endsWith(":delete") ? (
                                <LoaderCircleIcon className="size-4 animate-spin" />
                            ) : null}
                            Delete template
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
};

export default TemplatesPage;
