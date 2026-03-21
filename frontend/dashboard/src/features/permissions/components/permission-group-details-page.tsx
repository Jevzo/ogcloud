import { useDeferredValue, useEffect, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    ArrowLeftIcon,
    Clock3Icon,
    LoaderCircleIcon,
    PlusIcon,
    SearchIcon,
    ShieldAlertIcon,
    ShieldIcon,
    StarIcon,
    Trash2Icon,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router";
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
import { FieldError } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import PermissionGroupDeleteDialog from "@/features/permissions/components/permission-group-delete-dialog";
import PermissionGroupForm from "@/features/permissions/components/permission-group-form";
import { usePermissionGroupDetailsQuery } from "@/features/permissions/hooks/use-permission-group-details-query";
import {
    permissionGroupFormSchema,
    permissionNodeFormSchema,
} from "@/features/permissions/schemas";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import {
    addPermissionToGroup,
    listPersistedPlayers,
    removePermissionFromGroup,
    updatePermissionGroup,
} from "@/api";
import {
    buildUpdatePermissionGroupPayload,
    createEmptyPermissionGroupValues,
    toPermissionGroupFormValues,
} from "@/features/permissions/lib/permission-form";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import { formatDateTime } from "@/features/servers/lib/server-display";
import type { PermissionGroupFormValues } from "@/types/permission";

const PERMISSIONS_PAGE_SIZE = 10;

const StatCard = ({ helper, label, value }: { helper: string; label: string; value: string }) => (
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

const PermissionGroupDetailsSkeleton = () => (
    <div className="space-y-4">
        <Card className="border border-border/70 bg-card/85">
            <CardHeader>
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-9 w-72" />
                <Skeleton className="h-4 w-80" />
            </CardHeader>
            <CardContent className="flex flex-wrap gap-2">
                <Skeleton className="h-8 w-24" />
                <Skeleton className="h-8 w-28" />
                <Skeleton className="h-8 w-32" />
            </CardContent>
        </Card>

        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card
                    key={`permission-detail-stat-${index}`}
                    className="border border-border/70 bg-card/85"
                >
                    <CardHeader>
                        <Skeleton className="h-4 w-20" />
                        <Skeleton className="h-8 w-24" />
                    </CardHeader>
                    <CardContent>
                        <Skeleton className="h-4 w-32" />
                    </CardContent>
                </Card>
            ))}
        </div>

        <Card className="border border-border/70 bg-card/85">
            <CardHeader>
                <Skeleton className="h-4 w-36" />
                <Skeleton className="h-6 w-56" />
                <Skeleton className="h-4 w-96" />
            </CardHeader>
            <CardContent className="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
                <div className="space-y-4">
                    {Array.from({ length: 6 }).map((_, index) => (
                        <Skeleton key={`permission-config-left-${index}`} className="h-12 w-full" />
                    ))}
                </div>
                <div className="space-y-4">
                    {Array.from({ length: 5 }).map((_, index) => (
                        <Skeleton
                            key={`permission-config-right-${index}`}
                            className="h-28 w-full"
                        />
                    ))}
                </div>
            </CardContent>
        </Card>
    </div>
);

const PermissionGroupDetailsPage = () => {
    const params = useParams();
    const navigate = useNavigate();
    const getAccessToken = useAccessToken();
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const groupName = decodeURIComponent(params.groupName ?? "");
    const { errorMessage, group, isLoading, isRefreshing, lastUpdatedAt, refresh } =
        usePermissionGroupDetailsQuery(groupName);
    const groupForm = useForm<PermissionGroupFormValues>({
        resolver: zodResolver(permissionGroupFormSchema),
        defaultValues: createEmptyPermissionGroupValues(),
    });
    const permissionNodeForm = useForm<{ description: string; permission: string }>({
        resolver: zodResolver(permissionNodeFormSchema),
        defaultValues: {
            description: "",
            permission: "",
        },
    });

    const [permissionPageIndex, setPermissionPageIndex] = useState(0);
    const [permissionSearchInput, setPermissionSearchInput] = useState("");
    const [assignedPlayerCount, setAssignedPlayerCount] = useState<number | null>(null);
    const [isLoadingAssignedPlayerCount, setIsLoadingAssignedPlayerCount] = useState(false);
    const [isDeleteAlertOpen, setIsDeleteAlertOpen] = useState(false);
    const deferredPermissionQuery = useDeferredValue(permissionSearchInput.trim().toLowerCase());

    useEffect(() => {
        if (!group) {
            return;
        }

        groupForm.reset(toPermissionGroupFormValues(group));
    }, [group, groupForm]);

    useEffect(() => {
        setPermissionPageIndex(0);
    }, [groupName]);

    useEffect(() => {
        setPermissionPageIndex(0);
    }, [deferredPermissionQuery]);

    useEffect(() => {
        const permissionGroupId = group?.id.trim();

        if (!permissionGroupId) {
            setAssignedPlayerCount(null);
            setIsLoadingAssignedPlayerCount(false);
            return;
        }

        let cancelled = false;

        const loadAssignedPlayerCount = async () => {
            setIsLoadingAssignedPlayerCount(true);

            try {
                const accessToken = await getAccessToken();
                const playerPage = await listPersistedPlayers(accessToken, {
                    page: 0,
                    query: permissionGroupId,
                    size: 1,
                });

                if (cancelled) {
                    return;
                }

                setAssignedPlayerCount(playerPage.totalItems);
            } catch {
                if (cancelled) {
                    return;
                }

                setAssignedPlayerCount(null);
            } finally {
                if (!cancelled) {
                    setIsLoadingAssignedPlayerCount(false);
                }
            }
        };

        void loadAssignedPlayerCount();

        return () => {
            cancelled = true;
        };
    }, [getAccessToken, group?.id, lastUpdatedAt]);

    const handleSaveGroup = groupForm.handleSubmit(async (values) => {
        if (!group) {
            return;
        }

        if (!permissionSystemEnabled) {
            const message = "Permission system is disabled in network settings.";

            groupForm.setError("root", { message });
            toast.error(message);
            return;
        }

        try {
            const accessToken = await getAccessToken();
            const updatedGroup = await updatePermissionGroup(
                accessToken,
                group.id,
                buildUpdatePermissionGroupPayload(values, group.permissions),
            );

            toast.success(`Updated permission group ${updatedGroup.name}.`);
            await refresh(false);
        } catch (error) {
            const message =
                error instanceof Error ? error.message : "Unable to update permission group.";

            groupForm.setError("root", { message });
            toast.error(message);
        }
    });

    const handleAddPermission = permissionNodeForm.handleSubmit(async (values) => {
        if (!group) {
            return;
        }

        if (!permissionSystemEnabled) {
            const message = "Permission system is disabled in network settings.";

            permissionNodeForm.setError("root", { message });
            toast.error(message);
            return;
        }

        try {
            const accessToken = await getAccessToken();
            await addPermissionToGroup(accessToken, group.id, {
                description: values.description.trim(),
                perm: values.permission.trim(),
            });

            permissionNodeForm.reset({ description: "", permission: "" });
            toast.success(`Added permission to ${group.name}.`);
            await refresh(false);
        } catch (error) {
            const message =
                error instanceof Error
                    ? error.message
                    : "Unable to add permission to permission group.";

            permissionNodeForm.setError("root", { message });
            toast.error(message);
        }
    });

    const handleRemovePermission = async (permission: string) => {
        if (!group) {
            return;
        }

        if (!permissionSystemEnabled) {
            toast.error("Permission system is disabled in network settings.");
            return;
        }

        try {
            const accessToken = await getAccessToken();
            await removePermissionFromGroup(accessToken, group.id, permission);

            toast.success(`Removed permission from ${group.name}.`);
            await refresh(false);
        } catch (error) {
            toast.error(
                error instanceof Error
                    ? error.message
                    : "Unable to remove permission from permission group.",
            );
        }
    };

    const permissions = (group?.permissions ?? []).filter((permission) => {
        if (deferredPermissionQuery === "") {
            return true;
        }

        const normalizedPermission = permission.perm.toLowerCase();
        const normalizedDescription = permission.description.toLowerCase();

        return (
            normalizedPermission.includes(deferredPermissionQuery) ||
            normalizedDescription.includes(deferredPermissionQuery)
        );
    });
    const totalPermissionPages = Math.max(1, Math.ceil(permissions.length / PERMISSIONS_PAGE_SIZE));
    const visiblePermissions = permissions.slice(
        permissionPageIndex * PERMISSIONS_PAGE_SIZE,
        permissionPageIndex * PERMISSIONS_PAGE_SIZE + PERMISSIONS_PAGE_SIZE,
    );
    const assignedPlayerCountValue = isLoadingAssignedPlayerCount
        ? "Loading..."
        : assignedPlayerCount !== null
          ? `${assignedPlayerCount}`
          : "--";

    useEffect(() => {
        if (permissionPageIndex < totalPermissionPages) {
            return;
        }

        setPermissionPageIndex(Math.max(0, totalPermissionPages - 1));
    }, [permissionPageIndex, totalPermissionPages]);

    if (isLoading && !group) {
        return <PermissionGroupDetailsSkeleton />;
    }

    if (errorMessage && !group) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Permission Group Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load this permission group
                    </CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
                <CardFooter className="justify-between gap-3">
                    <Button variant="outline" asChild>
                        <Link to="/permissions">
                            <ArrowLeftIcon className="size-4" />
                            Back to groups
                        </Link>
                    </Button>
                </CardFooter>
            </Card>
        );
    }

    return (
        <div className="space-y-4">
            <div className="space-y-4">
                <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                    <Button variant="ghost" size="sm" asChild className="-ml-2 w-fit">
                        <Link to="/permissions">
                            <ArrowLeftIcon className="size-4" />
                            Back to permission groups
                        </Link>
                    </Button>

                    <div className="flex flex-wrap items-center gap-2 xl:justify-end">
                        <Button
                            variant="destructive"
                            onClick={() => setIsDeleteAlertOpen(true)}
                            disabled={!group}
                        >
                            <Trash2Icon className="size-4" />
                            Delete group
                        </Button>
                        <LastSyncSurface
                            isRefreshing={isRefreshing}
                            lastUpdatedAt={lastUpdatedAt}
                        />
                    </div>
                </div>

                <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                        <Badge
                            variant="outline"
                            className="border-primary/25 bg-primary/10 text-primary"
                        >
                            {group?.id ?? groupName}
                        </Badge>
                        {group?.default ? (
                            <Badge
                                variant="outline"
                                className="border-primary/30 bg-primary/10 text-primary"
                            >
                                <StarIcon className="size-3" />
                                Default group
                            </Badge>
                        ) : (
                            <Badge variant="outline" className="border-border/80">
                                Explicit assignment
                            </Badge>
                        )}
                    </div>

                    <div>
                        <CardTitle className="flex items-center gap-2 text-2xl tracking-tight">
                            <ShieldIcon className="size-5 text-primary" />
                            {group?.name ?? groupName}
                        </CardTitle>
                        <CardDescription className="mt-2 max-w-3xl text-sm leading-6">
                            Formatting, fallback handling, and live permission nodes for the
                            selected permission group.
                        </CardDescription>
                    </div>
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
                            This page is read-only until the permission system is enabled in network
                            settings.
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            {errorMessage && group ? (
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
                <StatCard
                    label="Group ID"
                    value={group?.id ?? "--"}
                    helper="Stable identifier referenced by assignments and moderation tools."
                />
                <StatCard
                    label="Weight"
                    value={group ? `${group.weight}` : "--"}
                    helper="Current precedence number stored for this rank."
                />
                <StatCard
                    label="Players inheriting"
                    value={assignedPlayerCountValue}
                    helper="Persisted players currently assigned to this permission group."
                />
                <StatCard
                    label="Default status"
                    value={group?.default ? "Enabled" : "Disabled"}
                    helper="Whether players fall back to this rank without an explicit assignment."
                />
            </div>

            <Tabs defaultValue="configuration" className="gap-4">
                <TabsList variant="line">
                    <TabsTrigger value="configuration">Configuration</TabsTrigger>
                    <TabsTrigger value="permissions">Permissions</TabsTrigger>
                </TabsList>

                <TabsContent value="configuration">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader className="border-b border-border/70 pb-4">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <div>
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Editable configuration
                                    </CardDescription>
                                    <CardTitle className="text-base">
                                        Formatting and precedence
                                    </CardTitle>
                                    <CardDescription>
                                        Identity fields stay locked here so assignments stay stable
                                        while the rest of the group is edited.
                                    </CardDescription>
                                </div>
                                {groupForm.formState.isDirty ? (
                                    <Badge
                                        variant="outline"
                                        className="w-fit border-amber-500/30 bg-amber-500/10 text-amber-300"
                                    >
                                        Unsaved changes
                                    </Badge>
                                ) : null}
                            </div>
                        </CardHeader>

                        <CardContent className="space-y-6 pt-4">
                            <PermissionGroupForm disableIdentityFields form={groupForm} />
                            <FieldError>{groupForm.formState.errors.root?.message}</FieldError>
                        </CardContent>
                        <CardFooter className="justify-end gap-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() =>
                                    group && groupForm.reset(toPermissionGroupFormValues(group))
                                }
                                disabled={
                                    !group ||
                                    groupForm.formState.isSubmitting ||
                                    !groupForm.formState.isDirty
                                }
                            >
                                Reset
                            </Button>
                            <Button
                                type="button"
                                onClick={() => void handleSaveGroup()}
                                disabled={
                                    !group ||
                                    !permissionSystemEnabled ||
                                    groupForm.formState.isSubmitting ||
                                    !groupForm.formState.isDirty
                                }
                            >
                                {groupForm.formState.isSubmitting ? (
                                    <>
                                        <LoaderCircleIcon className="size-4 animate-spin" />
                                        Saving
                                    </>
                                ) : (
                                    "Save changes"
                                )}
                            </Button>
                        </CardFooter>
                    </Card>
                </TabsContent>

                <TabsContent value="permissions">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader className="gap-3 border-b border-border/70 pb-4">
                            <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                                <div>
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Permission inventory
                                    </CardDescription>
                                    <CardTitle className="text-base">
                                        Direct permission nodes
                                    </CardTitle>
                                    <CardDescription>
                                        Search the current node set, manage descriptions, and page
                                        through the full permission list returned for this group.
                                    </CardDescription>
                                </div>

                                <div className="w-full xl:max-w-[320px]">
                                    <InputGroup>
                                        <InputGroupAddon>
                                            <SearchIcon className="size-4" />
                                        </InputGroupAddon>
                                        <InputGroupInput
                                            value={permissionSearchInput}
                                            onChange={(event) => {
                                                setPermissionSearchInput(event.target.value);
                                                setPermissionPageIndex(0);
                                            }}
                                            placeholder="Search permission or description"
                                        />
                                    </InputGroup>
                                </div>
                            </div>

                            <form
                                id="permission-node-form"
                                onSubmit={handleAddPermission}
                                className="grid gap-3 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)_auto]"
                            >
                                <InputGroup>
                                    <InputGroupAddon>
                                        <PlusIcon className="size-4" />
                                    </InputGroupAddon>
                                    <InputGroupInput
                                        placeholder="ogcloud.command.execute"
                                        disabled={
                                            !permissionSystemEnabled ||
                                            permissionNodeForm.formState.isSubmitting
                                        }
                                        {...permissionNodeForm.register("permission")}
                                    />
                                </InputGroup>
                                <Input
                                    placeholder="Permission description"
                                    disabled={
                                        !permissionSystemEnabled ||
                                        permissionNodeForm.formState.isSubmitting
                                    }
                                    {...permissionNodeForm.register("description")}
                                />
                                <Button
                                    type="submit"
                                    className="xl:self-start"
                                    disabled={
                                        !permissionSystemEnabled ||
                                        permissionNodeForm.formState.isSubmitting
                                    }
                                >
                                    {permissionNodeForm.formState.isSubmitting ? (
                                        <>
                                            <LoaderCircleIcon className="size-4 animate-spin" />
                                            Add
                                        </>
                                    ) : (
                                        <>
                                            <PlusIcon className="size-4" />
                                            Add
                                        </>
                                    )}
                                </Button>
                            </form>

                            <FieldError>
                                {permissionNodeForm.formState.errors.root?.message ??
                                    permissionNodeForm.formState.errors.permission?.message ??
                                    permissionNodeForm.formState.errors.description?.message}
                            </FieldError>
                        </CardHeader>

                        <CardContent className="px-0">
                            <Table>
                                <TableHeader>
                                    <TableRow className="hover:bg-transparent">
                                        <TableHead className="w-[34%] px-4">Permission</TableHead>
                                        <TableHead className="px-4">Description</TableHead>
                                        <TableHead className="w-16 px-4 text-right">
                                            Remove
                                        </TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {permissions.length === 0 ? (
                                        <TableRow>
                                            <TableCell
                                                colSpan={3}
                                                className="px-4 py-12 text-center text-sm text-muted-foreground"
                                            >
                                                No permissions matched the current filters.
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        visiblePermissions.map((permission) => (
                                            <TableRow key={permission.perm}>
                                                <TableCell className="px-4 py-3 font-mono text-xs text-foreground">
                                                    {permission.perm}
                                                </TableCell>
                                                <TableCell className="px-4 py-3 text-sm text-muted-foreground">
                                                    {permission.description}
                                                </TableCell>
                                                <TableCell className="px-4 py-3 text-right">
                                                    <Button
                                                        type="button"
                                                        variant="destructive"
                                                        size="icon-sm"
                                                        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                                                        onClick={() =>
                                                            void handleRemovePermission(
                                                                permission.perm,
                                                            )
                                                        }
                                                        disabled={
                                                            !permissionSystemEnabled || isRefreshing
                                                        }
                                                        aria-label={`Remove ${permission.perm}`}
                                                    >
                                                        <Trash2Icon className="size-4" />
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
                                Page {Math.min(permissionPageIndex + 1, totalPermissionPages)} of{" "}
                                {totalPermissionPages}
                            </div>
                            <div className="flex items-center gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() =>
                                        setPermissionPageIndex((value) => Math.max(0, value - 1))
                                    }
                                    disabled={permissionPageIndex === 0}
                                >
                                    Previous
                                </Button>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() =>
                                        setPermissionPageIndex((value) =>
                                            Math.min(totalPermissionPages - 1, value + 1),
                                        )
                                    }
                                    disabled={permissionPageIndex >= totalPermissionPages - 1}
                                >
                                    Next
                                </Button>
                            </div>
                        </CardFooter>
                    </Card>
                </TabsContent>
            </Tabs>

            <PermissionGroupDeleteDialog
                groupId={group?.id}
                groupName={group?.name ?? groupName}
                onDeleted={() => navigate("/permissions", { replace: true })}
                onOpenChange={setIsDeleteAlertOpen}
                open={isDeleteAlertOpen}
            />
        </div>
    );
};

export default PermissionGroupDetailsPage;
