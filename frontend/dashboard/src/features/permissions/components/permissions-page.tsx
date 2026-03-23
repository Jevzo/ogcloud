import { useDeferredValue, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    Clock3Icon,
    LoaderCircleIcon,
    PlusIcon,
    SearchIcon,
    ShieldAlertIcon,
    ShieldIcon,
    StarIcon,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
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
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { FieldError } from "@/components/ui/field";
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
import PermissionGroupActionsMenu from "@/features/permissions/components/permission-group-actions-menu";
import PermissionGroupDeleteDialog from "@/features/permissions/components/permission-group-delete-dialog";
import PermissionGroupForm from "@/features/permissions/components/permission-group-form";
import { usePermissionGroupsQuery } from "@/features/permissions/hooks/use-permission-groups-query";
import { permissionGroupFormSchema } from "@/features/permissions/schemas";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { createPermissionGroup } from "@/api";
import {
    buildCreatePermissionGroupPayload,
    createEmptyPermissionGroupValues,
} from "@/features/permissions/lib/permission-form";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { PermissionGroupFormValues, PermissionGroupRecord } from "@/types/permission";

const PERMISSIONS_PAGE_SIZE = 10;

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

const PermissionsTableSkeleton = () => (
    <div className="space-y-2 px-5 pb-5">
        {Array.from({ length: 6 }).map((_, index) => (
            <Skeleton key={`permissions-table-skeleton-${index}`} className="h-12 w-full" />
        ))}
    </div>
);

const PermissionsPageSkeleton = () => (
    <div className="space-y-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="space-y-3">
                <Skeleton className="h-9 w-44" />
                <Skeleton className="h-4 w-96" />
            </div>
            <Skeleton className="h-10 w-48" />
        </div>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card
                    key={`permission-summary-skeleton-${index}`}
                    className="border border-border/70 bg-card/85"
                >
                    <CardHeader>
                        <Skeleton className="h-4 w-24" />
                        <Skeleton className="h-8 w-24" />
                    </CardHeader>
                    <CardContent>
                        <Skeleton className="h-4 w-40" />
                    </CardContent>
                </Card>
            ))}
        </div>

        <Card className="border border-border/70 bg-card/85">
            <CardHeader className="gap-3 border-b border-border/70 pb-4">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                    <div className="space-y-2">
                        <Skeleton className="h-4 w-28" />
                        <Skeleton className="h-6 w-44" />
                        <Skeleton className="h-4 w-72" />
                    </div>
                    <div className="w-full space-y-2 xl:max-w-[420px]">
                        <Skeleton className="h-10 w-full" />
                        <Skeleton className="h-10 w-32 xl:ml-auto" />
                    </div>
                </div>
            </CardHeader>
            <CardContent className="px-0">
                <PermissionsTableSkeleton />
            </CardContent>
            <CardFooter className="justify-between gap-3">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="h-10 w-32" />
            </CardFooter>
        </Card>
    </div>
);

const getDefaultBadgeClassName = (isDefault: boolean) =>
    isDefault
        ? "border-primary/30 bg-primary/10 text-primary"
        : "border-border/80 bg-background text-muted-foreground";

const PermissionsPage = () => {
    const navigate = useNavigate();
    const getAccessToken = useAccessToken();
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );

    const [currentPage, setCurrentPage] = useState(0);
    const [searchInput, setSearchInput] = useState("");
    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
    const [groupPendingDeletion, setGroupPendingDeletion] = useState<PermissionGroupRecord | null>(
        null,
    );

    const deferredQuery = useDeferredValue(searchInput.trim());
    const {
        data: groups,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    } = usePermissionGroupsQuery({
        query: deferredQuery,
    });
    const form = useForm<PermissionGroupFormValues>({
        resolver: zodResolver(permissionGroupFormSchema),
        defaultValues: createEmptyPermissionGroupValues(),
    });

    const handleCreateDialogChange = (nextOpen: boolean) => {
        if (!nextOpen && form.formState.isSubmitting) {
            return;
        }

        setIsCreateDialogOpen(nextOpen);

        if (!nextOpen) {
            form.reset(createEmptyPermissionGroupValues());
        }
    };

    const handleCreatePermissionGroup = form.handleSubmit(async (values) => {
        if (!permissionSystemEnabled) {
            const message = "Permission system is disabled in network settings.";

            form.setError("root", { message });
            toast.error(message);
            return;
        }

        try {
            const accessToken = await getAccessToken();
            const createdGroup = await createPermissionGroup(
                accessToken,
                buildCreatePermissionGroupPayload(values),
            );

            toast.success(`Created permission group ${createdGroup.name}.`);
            handleCreateDialogChange(false);
            await refresh(false);
            setCurrentPage(0);
        } catch (error) {
            const message =
                error instanceof Error ? error.message : "Unable to create permission group.";

            form.setError("root", { message });
            toast.error(message);
        }
    });

    const handleDeleteGroup = async () => {
        await refresh(false);
    };

    const defaultCount = groups.filter((group) => group.default).length;
    const explicitPermissionCount = groups.reduce(
        (total, group) => total + group.permissions.length,
        0,
    );
    const totalPages = getPaginatedTotalPages({
        items: groups,
        page: currentPage,
        size: PERMISSIONS_PAGE_SIZE,
        totalItems: groups.length,
    });
    const maxPage = Math.max(0, totalPages - 1);
    const visiblePage = Math.min(currentPage, maxPage);
    const visibleGroups = groups.slice(
        visiblePage * PERMISSIONS_PAGE_SIZE,
        (visiblePage + 1) * PERMISSIONS_PAGE_SIZE,
    );
    const hasFreshData = lastUpdatedAt !== null;

    if (isLoading && !hasFreshData) {
        return <PermissionsPageSkeleton />;
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Permissions Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load permission groups
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
                        Access control
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Rank precedence, fallback groups, and explicit permission grants for
                        dashboard-managed roles.
                    </p>
                </div>

                <div className="flex flex-col gap-3 sm:flex-row sm:items-center lg:justify-end">
                    <Button
                        onClick={() => setIsCreateDialogOpen(true)}
                        disabled={!permissionSystemEnabled}
                    >
                        <PlusIcon className="size-4" />
                        Create group
                    </Button>
                    <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
                </div>
            </div>

            {!permissionSystemEnabled ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="flex items-center gap-2 text-base text-amber-200">
                            <ShieldAlertIcon className="size-4" />
                            Permission system is disabled
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            This route is read-only until the permission system is enabled in
                            network settings.
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful permission snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Groups"
                    value={`${groups.length}`}
                    helper={
                        deferredQuery
                            ? "Groups matching the current search query."
                            : "Permission groups currently visible to the dashboard."
                    }
                />
                <SummaryCard
                    label="Default ranks"
                    value={`${defaultCount}`}
                    helper="Fallback groups assigned when players have no explicit rank."
                />
                <SummaryCard
                    label="Explicit grants"
                    value={`${explicitPermissionCount}`}
                    helper="Total direct permission nodes across all visible groups."
                />
                <SummaryCard
                    label="System state"
                    value={permissionSystemEnabled ? "Enabled" : "Disabled"}
                    helper="Whether edits can currently be applied through the dashboard."
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-3 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Role inventory
                            </CardDescription>
                            <CardTitle className="text-base">
                                Permission groups and precedence
                            </CardTitle>
                            <CardDescription>
                                Search the current rank set, inspect direct grants, and open the
                                full group editor for formatting or node changes.
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
                                    placeholder="Search group, name, or node"
                                />
                            </InputGroup>
                        </div>
                    </div>
                </CardHeader>

                <CardContent className="px-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-transparent">
                                <TableHead className="px-4">Group</TableHead>
                                <TableHead className="px-4">Weight</TableHead>
                                <TableHead className="px-4">Default</TableHead>
                                <TableHead className="px-4">Explicit grants</TableHead>
                                <TableHead className="px-4 text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {groups.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={5}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No permission groups matched the current filters.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                visibleGroups.map((group) => (
                                    <TableRow
                                        key={group.id}
                                        className="cursor-pointer"
                                        onClick={() =>
                                            navigate(`/permissions/${encodeURIComponent(group.id)}`)
                                        }
                                    >
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="flex flex-wrap items-center gap-2 font-medium text-foreground">
                                                    <ShieldIcon className="size-4 text-primary" />
                                                    {group.name}
                                                    {group.default ? (
                                                        <Badge
                                                            variant="outline"
                                                            className="border-primary/30 bg-primary/10 text-primary"
                                                        >
                                                            <StarIcon className="size-3" />
                                                            Default
                                                        </Badge>
                                                    ) : null}
                                                </div>
                                                <div className="font-mono text-xs text-muted-foreground">
                                                    {group.id}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                            {group.weight}
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <Badge
                                                variant="outline"
                                                className={getDefaultBadgeClassName(group.default)}
                                            >
                                                {group.default
                                                    ? "Fallback rank"
                                                    : "Explicit assignment"}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {group.permissions.length}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    Direct nodes inherited by assigned players
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell
                                            className="px-4 py-3 text-right align-top"
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            <PermissionGroupActionsMenu
                                                group={group}
                                                onDeleteGroup={setGroupPendingDeletion}
                                                onOpenGroup={(nextGroup) =>
                                                    navigate(
                                                        `/permissions/${encodeURIComponent(nextGroup.id)}`,
                                                    )
                                                }
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
                        Page {Math.min(visiblePage + 1, totalPages)} of {totalPages}
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            onClick={() => setCurrentPage(Math.max(0, visiblePage - 1))}
                            disabled={visiblePage === 0 || isRefreshing}
                        >
                            Previous
                        </Button>
                        <Button
                            variant="outline"
                            onClick={() => setCurrentPage(visiblePage + 1)}
                            disabled={
                                !getPaginatedHasNext({
                                    items: groups,
                                    page: visiblePage,
                                    size: PERMISSIONS_PAGE_SIZE,
                                    totalItems: groups.length,
                                }) || isRefreshing
                            }
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>

            <Dialog open={isCreateDialogOpen} onOpenChange={handleCreateDialogChange}>
                <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-4xl">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <ShieldIcon className="size-4 text-primary" />
                            Create permission group
                        </DialogTitle>
                        <DialogDescription>
                            Define a new rank with formatting, precedence, and default fallback
                            behavior.
                        </DialogDescription>
                    </DialogHeader>

                    <form onSubmit={handleCreatePermissionGroup} className="space-y-6">
                        <PermissionGroupForm form={form} />

                        <FieldError>{form.formState.errors.root?.message}</FieldError>

                        <DialogFooter>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => handleCreateDialogChange(false)}
                                disabled={form.formState.isSubmitting}
                            >
                                Cancel
                            </Button>
                            <Button
                                type="submit"
                                disabled={form.formState.isSubmitting || !permissionSystemEnabled}
                            >
                                {form.formState.isSubmitting ? (
                                    <>
                                        <LoaderCircleIcon className="size-4 animate-spin" />
                                        Creating
                                    </>
                                ) : (
                                    <>
                                        <PlusIcon className="size-4" />
                                        Create group
                                    </>
                                )}
                            </Button>
                        </DialogFooter>
                    </form>
                </DialogContent>
            </Dialog>

            <PermissionGroupDeleteDialog
                groupId={groupPendingDeletion?.id}
                groupName={groupPendingDeletion?.name ?? ""}
                onDeleted={handleDeleteGroup}
                onOpenChange={(open) => {
                    if (open) {
                        return;
                    }

                    setGroupPendingDeletion(null);
                }}
                open={groupPendingDeletion !== null}
            />
        </div>
    );
};

export default PermissionsPage;
