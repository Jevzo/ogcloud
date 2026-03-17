import { useEffect, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    ArrowLeftIcon,
    LoaderCircleIcon,
    PlusIcon,
    ShieldAlertIcon,
    ShieldIcon,
    StarIcon,
    Trash2Icon,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router";
import { toast } from "sonner";

import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogMedia,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
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
import { FieldError } from "@/components/ui/field";
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
import PermissionGroupForm from "@/features/permissions/components/permission-group-form";
import { usePermissionGroupDetailsQuery } from "@/features/permissions/hooks/use-permission-group-details-query";
import {
    permissionGroupFormSchema,
    permissionNodeFormSchema,
} from "@/features/permissions/schemas";
import { useAccessToken } from "@/hooks/use-access-token";
import {
    addPermissionToGroup,
    deletePermissionGroup,
    removePermissionFromGroup,
    updatePermissionGroup,
} from "@/lib/api";
import {
    buildUpdatePermissionGroupPayload,
    createEmptyPermissionGroupValues,
    toPermissionGroupFormValues,
} from "@/lib/permission-form";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import { formatDateTime } from "@/lib/server-display";
import type { PermissionGroupFormValues } from "@/types/permission";

const PERMISSIONS_PAGE_SIZE = 10;

const StatCard = ({
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
                <Card key={`permission-detail-stat-${index}`} className="border border-border/70 bg-card/85">
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

        <div className="grid gap-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
            <Card className="border border-border/70 bg-card/85">
                <CardHeader>
                    <Skeleton className="h-6 w-40" />
                </CardHeader>
                <CardContent className="space-y-4">
                    {Array.from({ length: 5 }).map((_, index) => (
                        <Skeleton key={`permission-list-skeleton-${index}`} className="h-12 w-full" />
                    ))}
                </CardContent>
            </Card>

            <Card className="border border-border/70 bg-card/85">
                <CardHeader>
                    <Skeleton className="h-6 w-48" />
                </CardHeader>
                <CardContent className="space-y-4">
                    {Array.from({ length: 5 }).map((_, index) => (
                        <Skeleton key={`permission-form-skeleton-${index}`} className="h-28 w-full" />
                    ))}
                </CardContent>
            </Card>
        </div>
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
    const {
        errorMessage,
        group,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    } = usePermissionGroupDetailsQuery(groupName);
    const groupForm = useForm<PermissionGroupFormValues>({
        resolver: zodResolver(permissionGroupFormSchema),
        defaultValues: createEmptyPermissionGroupValues(),
    });
    const permissionNodeForm = useForm<{ permission: string }>({
        resolver: zodResolver(permissionNodeFormSchema),
        defaultValues: {
            permission: "",
        },
    });

    const [permissionPageIndex, setPermissionPageIndex] = useState(0);
    const [isDeleteAlertOpen, setIsDeleteAlertOpen] = useState(false);
    const [isDeletingGroup, setIsDeletingGroup] = useState(false);

    useEffect(() => {
        if (!group) {
            return;
        }

        groupForm.reset(toPermissionGroupFormValues(group));
    }, [group, groupForm]);

    useEffect(() => {
        setPermissionPageIndex(0);
    }, [groupName]);

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
            await addPermissionToGroup(accessToken, group.id, values.permission.trim());

            permissionNodeForm.reset({ permission: "" });
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

    const handleDeleteGroup = async () => {
        if (!group) {
            return;
        }

        if (!permissionSystemEnabled) {
            toast.error("Permission system is disabled in network settings.");
            return;
        }

        setIsDeletingGroup(true);

        try {
            const accessToken = await getAccessToken();

            await deletePermissionGroup(accessToken, group.id);
            toast.success(`Deleted permission group ${group.name}.`);
            navigate("/permissions", { replace: true });
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to delete permission group.",
            );
        } finally {
            setIsDeletingGroup(false);
            setIsDeleteAlertOpen(false);
        }
    };

    const permissions = group?.permissions ?? [];
    const totalPermissionPages = Math.max(1, Math.ceil(permissions.length / PERMISSIONS_PAGE_SIZE));
    const visiblePermissions = permissions.slice(
        permissionPageIndex * PERMISSIONS_PAGE_SIZE,
        permissionPageIndex * PERMISSIONS_PAGE_SIZE + PERMISSIONS_PAGE_SIZE,
    );

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
            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div className="space-y-3">
                            <Button variant="ghost" size="sm" asChild className="-ml-2 w-fit">
                                <Link to="/permissions">
                                    <ArrowLeftIcon className="size-4" />
                                    Back to permission groups
                                </Link>
                            </Button>
                            <div className="space-y-2">
                                <div className="flex flex-wrap items-center gap-2">
                                    <Badge variant="outline" className="border-primary/25 bg-primary/10 text-primary">
                                        Permission details
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
                                    {lastUpdatedAt ? (
                                        <Badge variant="outline" className="border-border/80">
                                            Last sync {formatDateTime(new Date(lastUpdatedAt).toISOString())}
                                        </Badge>
                                    ) : null}
                                </div>
                                <div>
                                    <CardTitle className="flex items-center gap-2 text-2xl tracking-tight">
                                        <ShieldIcon className="size-5 text-primary" />
                                        {group?.name ?? groupName}
                                    </CardTitle>
                                    <CardDescription className="mt-2 max-w-3xl text-sm leading-6">
                                        Edit formatting, precedence, and explicit permission nodes
                                        inherited by members assigned to this group.
                                    </CardDescription>
                                </div>
                            </div>
                        </div>

                        <CardAction className="col-auto row-auto">
                            <div className="flex flex-wrap items-center gap-2">
                                <Button
                                    variant="destructive"
                                    onClick={() => setIsDeleteAlertOpen(true)}
                                    disabled={!group}
                                >
                                    <Trash2Icon className="size-4" />
                                    Delete group
                                </Button>
                            </div>
                        </CardAction>
                    </div>
                </CardHeader>
            </Card>

            {!permissionSystemEnabled ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="flex items-center gap-2 text-base text-amber-200">
                            <ShieldAlertIcon className="size-4" />
                            Permission system is disabled
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            This page is read-only until the permission system is enabled in
                            network settings.
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
                    label="Explicit grants"
                    value={group ? `${group.permissions.length}` : "--"}
                    helper="Direct permission nodes inherited by assigned members."
                />
                <StatCard
                    label="Default status"
                    value={group?.default ? "Enabled" : "Disabled"}
                    helper="Whether players fall back to this rank without an explicit assignment."
                />
            </div>

            <div className="grid gap-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
                <div className="space-y-4">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader className="pb-4">
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Inheritance posture
                            </CardDescription>
                            <CardTitle className="text-base">Assignment and propagation</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="flex flex-wrap gap-2">
                                <Badge
                                    variant="outline"
                                    className={
                                        group?.default
                                            ? "border-primary/30 bg-primary/10 text-primary"
                                            : "border-border/80"
                                    }
                                >
                                    {group?.default ? "Default fallback rank" : "Assigned explicitly"}
                                </Badge>
                                <Badge
                                    variant="outline"
                                    className="border-sky-500/30 bg-sky-500/10 text-sky-300"
                                >
                                    Members inherit direct grants immediately
                                </Badge>
                            </div>
                            <p className="text-sm text-muted-foreground">
                                Changes to the permission list apply to online players as soon as
                                the backend processes the update.
                            </p>
                        </CardContent>
                    </Card>

                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader className="gap-4 border-b border-border/70 pb-4">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                <div>
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Explicit permissions
                                    </CardDescription>
                                    <CardTitle className="text-base">Direct permission nodes</CardTitle>
                                    <CardDescription>
                                        Add or remove direct grants from this group without a
                                        separate save step.
                                    </CardDescription>
                                </div>
                            </div>

                            <form onSubmit={handleAddPermission} className="space-y-3">
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
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                    <FieldError>
                                        {permissionNodeForm.formState.errors.root?.message ??
                                            permissionNodeForm.formState.errors.permission?.message}
                                    </FieldError>
                                    <Button
                                        type="submit"
                                        disabled={
                                            !permissionSystemEnabled ||
                                            permissionNodeForm.formState.isSubmitting
                                        }
                                    >
                                        {permissionNodeForm.formState.isSubmitting ? (
                                            <>
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                                Adding
                                            </>
                                        ) : (
                                            <>
                                                <PlusIcon className="size-4" />
                                                Add permission
                                            </>
                                        )}
                                    </Button>
                                </div>
                            </form>
                        </CardHeader>

                        <CardContent className="px-0">
                            <Table>
                                <TableHeader>
                                    <TableRow className="hover:bg-transparent">
                                        <TableHead className="px-4">Permission</TableHead>
                                        <TableHead className="px-4">Propagation</TableHead>
                                        <TableHead className="px-4 text-right">Remove</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {visiblePermissions.length === 0 ? (
                                        <TableRow>
                                            <TableCell
                                                colSpan={3}
                                                className="px-4 py-12 text-center text-sm text-muted-foreground"
                                            >
                                                No explicit permissions are currently assigned.
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        visiblePermissions.map((permission) => (
                                            <TableRow key={permission}>
                                                <TableCell className="px-4 py-3 font-mono text-xs text-foreground">
                                                    {permission}
                                                </TableCell>
                                                <TableCell className="px-4 py-3">
                                                    <Badge
                                                        variant="outline"
                                                        className="border-sky-500/30 bg-sky-500/10 text-sky-300"
                                                    >
                                                        Inherited by members
                                                    </Badge>
                                                </TableCell>
                                                <TableCell className="px-4 py-3 text-right">
                                                    <Button
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={() =>
                                                            void handleRemovePermission(permission)
                                                        }
                                                        disabled={
                                                            !permissionSystemEnabled || isRefreshing
                                                        }
                                                    >
                                                        <Trash2Icon className="size-4 text-destructive" />
                                                        Remove
                                                    </Button>
                                                </TableCell>
                                            </TableRow>
                                        ))
                                    )}
                                </TableBody>
                            </Table>
                        </CardContent>
                        {permissions.length > PERMISSIONS_PAGE_SIZE ? (
                            <CardFooter className="justify-between gap-3">
                                <div className="text-sm text-muted-foreground">
                                    Page {permissionPageIndex + 1} of {totalPermissionPages}
                                </div>
                                <div className="flex items-center gap-2">
                                    <Button
                                        variant="outline"
                                        onClick={() =>
                                            setPermissionPageIndex((value) =>
                                                Math.max(0, value - 1),
                                            )
                                        }
                                        disabled={permissionPageIndex === 0}
                                    >
                                        Previous
                                    </Button>
                                    <Button
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
                        ) : null}
                    </Card>
                </div>

                <Card className="self-start border border-border/70 bg-card/85 shadow-none">
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
                                    Update identity rendering, fallback behavior, and ordering for
                                    this permission group.
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

                    <form onSubmit={handleSaveGroup}>
                        <CardContent className="space-y-6 pt-4">
                            <PermissionGroupForm disableIdentityFields form={groupForm} />

                            <FieldError>{groupForm.formState.errors.root?.message}</FieldError>
                        </CardContent>
                        <CardFooter className="justify-end gap-2">
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => group && groupForm.reset(toPermissionGroupFormValues(group))}
                                disabled={
                                    !group ||
                                    groupForm.formState.isSubmitting ||
                                    !groupForm.formState.isDirty
                                }
                            >
                                Reset
                            </Button>
                            <Button
                                type="submit"
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
                    </form>
                </Card>
            </div>

            <AlertDialog open={isDeleteAlertOpen} onOpenChange={setIsDeleteAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-destructive/10 text-destructive">
                            <Trash2Icon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>Delete {group?.name ?? groupName}?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Players currently assigned to this group will fall back to the default
                            permission group after deletion.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isDeletingGroup}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            variant="destructive"
                            disabled={isDeletingGroup}
                            onClick={() => void handleDeleteGroup()}
                        >
                            {isDeletingGroup ? "Deleting..." : "Delete group"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
};

export default PermissionGroupDetailsPage;
