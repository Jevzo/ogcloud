import { useDeferredValue, useEffect, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    Clock3Icon,
    Layers3Icon,
    LoaderCircleIcon,
    PlusIcon,
    SearchIcon,
    ShieldAlertIcon,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

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
import { createServerGroup, restartServerGroup, toggleServerGroupMaintenance } from "@/api";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import GroupActionsMenu from "@/features/groups/components/group-actions-menu";
import GroupConfigurationForm from "@/features/groups/components/group-configuration-form";
import { useGroupFormOptionsQuery } from "@/features/groups/hooks/use-group-form-options-query";
import { useGroupsQuery } from "@/features/groups/hooks/use-groups-query";
import { buildCreateGroupPayload, createEmptyGroupValues } from "@/features/groups/lib/group-form";
import { getRuntimeProfileLabel } from "@/features/groups/lib/group-runtime";
import { groupFormSchema } from "@/features/groups/schemas";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { GroupFormValues, GroupRecord } from "@/types/group";
import type { TemplateRecord } from "@/types/template";
import { useNavigate } from "react-router";

const buildInitialGroupValues = (templates: TemplateRecord[]): GroupFormValues => {
    const initialValues = createEmptyGroupValues();
    const firstTemplate = templates[0];

    if (!firstTemplate) {
        return initialValues;
    }

    return {
        ...initialValues,
        templatePath: firstTemplate.group,
        templateVersion: firstTemplate.version,
    };
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

const GroupsTableSkeleton = () => (
    <div className="space-y-2 px-5 pb-5">
        {Array.from({ length: 8 }).map((_, index) => (
            <Skeleton key={`groups-table-skeleton-${index}`} className="h-12 w-full" />
        ))}
    </div>
);

const GroupsPageSkeleton = () => (
    <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card
                    key={`group-summary-skeleton-${index}`}
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
            <CardHeader>
                <Skeleton className="h-4 w-28" />
                <Skeleton className="h-8 w-44" />
                <Skeleton className="h-4 w-72" />
            </CardHeader>
            <CardContent className="space-y-4">
                <Skeleton className="h-10 w-full" />
            </CardContent>
            <GroupsTableSkeleton />
        </Card>
    </div>
);

const getTypeBadgeClassName = (type: GroupRecord["type"]) => {
    if (type === "STATIC") {
        return "border-amber-500/30 bg-amber-500/10 text-amber-300";
    }

    if (type === "PROXY") {
        return "border-sky-500/30 bg-sky-500/10 text-sky-300";
    }

    return "border-primary/30 bg-primary/10 text-primary";
};

const GroupsPage = () => {
    const navigate = useNavigate();
    const getAccessToken = useAccessToken();

    const [currentPage, setCurrentPage] = useState(0);
    const [searchInput, setSearchInput] = useState("");
    const [activeActionKey, setActiveActionKey] = useState<string | null>(null);
    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

    const deferredQuery = useDeferredValue(searchInput.trim());
    const {
        data: groupPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    } = useGroupsQuery({
        currentPage,
        query: deferredQuery,
    });
    const { data: templates, errorMessage: templatesErrorMessage } = useGroupFormOptionsQuery();
    const form = useForm<GroupFormValues>({
        resolver: zodResolver(groupFormSchema),
        defaultValues: buildInitialGroupValues(templates),
    });

    useEffect(() => {
        if (!templates.length) {
            return;
        }

        const currentTemplatePath = form.getValues("templatePath");
        const currentTemplateVersion = form.getValues("templateVersion");

        if (currentTemplatePath && currentTemplateVersion) {
            return;
        }

        form.setValue("templatePath", templates[0].group, {
            shouldDirty: false,
            shouldTouch: false,
            shouldValidate: false,
        });
        form.setValue("templateVersion", templates[0].version, {
            shouldDirty: false,
            shouldTouch: false,
            shouldValidate: false,
        });
    }, [form, templates]);

    const handleCreateDialogChange = (nextOpen: boolean) => {
        if (!nextOpen && form.formState.isSubmitting) {
            return;
        }

        setIsCreateDialogOpen(nextOpen);

        if (!nextOpen) {
            form.reset(buildInitialGroupValues(templates));
        }
    };

    const handleCreateGroup = form.handleSubmit(async (values) => {
        try {
            const accessToken = await getAccessToken();
            const createdGroup = await createServerGroup(
                accessToken,
                buildCreateGroupPayload(values),
            );

            toast.success(`Created server group ${createdGroup.id}.`);
            handleCreateDialogChange(false);
            if (currentPage === 0) {
                await refresh(false);
            } else {
                setCurrentPage(0);
            }
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to create group.";

            form.setError("root", {
                message,
            });
            toast.error(message);
        }
    });

    const handleToggleMaintenance = async (groupId: string, maintenance: boolean) => {
        const actionKey = `${groupId}:maintenance:${maintenance ? "on" : "off"}`;
        setActiveActionKey(actionKey);

        try {
            const accessToken = await getAccessToken();
            const updatedGroup = await toggleServerGroupMaintenance(
                accessToken,
                groupId,
                maintenance,
            );

            toast.success(
                `${updatedGroup.id} maintenance ${maintenance ? "enabled" : "disabled"}.`,
            );
            await refresh(false);
        } catch (error) {
            const nextError =
                error instanceof Error ? error : new Error("Unable to update maintenance mode.");

            toast.error(nextError.message);
            throw nextError;
        } finally {
            setActiveActionKey(null);
        }
    };

    const handleRestartGroup = async (groupId: string) => {
        const actionKey = `${groupId}:restart`;
        setActiveActionKey(actionKey);

        try {
            const accessToken = await getAccessToken();
            await restartServerGroup(accessToken, groupId);
            toast.success(`${groupId} restart requested.`);
            await refresh(false);
        } catch (error) {
            const nextError =
                error instanceof Error ? error : new Error("Unable to restart group.");

            toast.error(nextError.message);
            throw nextError;
        } finally {
            setActiveActionKey(null);
        }
    };

    const maintenanceCount = groupPage.items.filter((group) => group.maintenance).length;
    const staticCount = groupPage.items.filter((group) => group.type === "STATIC").length;
    const proxyCount = groupPage.items.filter((group) => group.type === "PROXY").length;
    const totalPages = getPaginatedTotalPages(groupPage);
    const hasFreshData = lastUpdatedAt !== null;

    if (isLoading && !hasFreshData) {
        return <GroupsPageSkeleton />;
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Groups Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load the group catalog
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
                        Server groups
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Manage runtime templates, autoscaling posture, and resource envelopes for
                        every deployable group.
                    </p>
                </div>

                <div className="flex flex-col gap-3 sm:flex-row sm:items-center lg:justify-end">
                    <Button onClick={() => setIsCreateDialogOpen(true)}>
                        <PlusIcon className="size-4" />
                        Create group
                    </Button>
                    <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
                </div>
            </div>

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful group snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            {templatesErrorMessage ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="flex items-center gap-2 text-base text-amber-200">
                            <ShieldAlertIcon className="size-4" />
                            Template catalog is unavailable
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {templatesErrorMessage}. You can still enter template path and version
                            manually while creating or editing groups.
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Total groups"
                    value={`${groupPage.totalItems}`}
                    helper={
                        deferredQuery
                            ? "Groups matching the active search query."
                            : "Paginated deployable groups currently visible to the dashboard."
                    }
                />
                <SummaryCard
                    label="Maintenance on page"
                    value={`${maintenanceCount}`}
                    helper="Groups on the current page that are held out of normal player traffic."
                />
                <SummaryCard
                    label="Static on page"
                    value={`${staticCount}`}
                    helper="Persistent groups on the current page retaining attached storage."
                />
                <SummaryCard
                    label="Proxy on page"
                    value={`${proxyCount}`}
                    helper="Velocity-facing groups represented on the current page."
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-3 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Group inventory
                            </CardDescription>
                            <CardTitle className="text-base">Deployable runtime groups</CardTitle>
                            <CardDescription>
                                Search across group IDs and runtime posture, then use row actions
                                for maintenance control or restart requests.
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
                                    placeholder="Search groups, runtime, or image"
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
                                <TableHead className="px-4">Runtime</TableHead>
                                <TableHead className="px-4">Template</TableHead>
                                <TableHead className="px-4">Scaling</TableHead>
                                <TableHead className="px-4">Resources</TableHead>
                                <TableHead className="px-4">Maintenance</TableHead>
                                <TableHead className="px-4">Updated</TableHead>
                                <TableHead className="px-4 text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {groupPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={8}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No groups matched the current filters.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                groupPage.items.map((group) => (
                                    <TableRow
                                        key={group.id}
                                        className="cursor-pointer"
                                        onClick={() =>
                                            navigate(`/groups/${encodeURIComponent(group.id)}`)
                                        }
                                    >
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <span className="font-medium text-foreground">
                                                        {group.id}
                                                    </span>
                                                    <span>
                                                        <span
                                                            className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${getTypeBadgeClassName(group.type)}`}
                                                        >
                                                            {group.type}
                                                        </span>
                                                    </span>
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {group.serverImage}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {getRuntimeProfileLabel(group.runtimeProfile)}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    Drain timeout {group.drainTimeoutSeconds}s
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1">
                                                <div className="font-medium text-foreground">
                                                    {group.templatePath}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {group.templateVersion} / {group.templateBucket}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1 text-sm">
                                                <div className="font-medium text-foreground">
                                                    {group.scaling.minOnline} min /{" "}
                                                    {group.scaling.maxInstances} max
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    {group.scaling.playersPerServer} players per
                                                    server
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <div className="space-y-1 text-sm">
                                                <div className="font-medium text-foreground">
                                                    {group.resources.memoryRequest} /{" "}
                                                    {group.resources.memoryLimit}
                                                </div>
                                                <div className="text-xs text-muted-foreground">
                                                    CPU {group.resources.cpuRequest} /{" "}
                                                    {group.resources.cpuLimit}
                                                </div>
                                            </div>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top">
                                            <span
                                                className={`inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium ${
                                                    group.maintenance
                                                        ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                                        : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                }`}
                                            >
                                                {group.maintenance ? "Enabled" : "Disabled"}
                                            </span>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                            {formatDateTime(group.updatedAt)}
                                        </TableCell>
                                        <TableCell
                                            className="px-4 py-3 text-right align-top"
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            <GroupActionsMenu
                                                activeActionKey={activeActionKey}
                                                group={group}
                                                onOpenGroup={(nextGroup) =>
                                                    navigate(
                                                        `/groups/${encodeURIComponent(nextGroup.id)}`,
                                                    )
                                                }
                                                onRestartGroup={handleRestartGroup}
                                                onToggleMaintenance={handleToggleMaintenance}
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
                        Page {groupPage.page + 1} of {totalPages}
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
                            disabled={!getPaginatedHasNext(groupPage) || isRefreshing}
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>

            <Dialog open={isCreateDialogOpen} onOpenChange={handleCreateDialogChange}>
                <DialogContent className="max-h-[92vh] overflow-y-auto sm:max-w-5xl">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <Layers3Icon className="size-4 text-primary" />
                            Create server group
                        </DialogTitle>
                        <DialogDescription>
                            Define a new deployable server group with its runtime, scaling, and
                            resource envelope.
                        </DialogDescription>
                    </DialogHeader>

                    <form onSubmit={handleCreateGroup} className="space-y-6">
                        <GroupConfigurationForm
                            form={form}
                            lockTemplateFieldsToKnownSelection
                            templates={templates}
                        />

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
                            <Button type="submit" disabled={form.formState.isSubmitting}>
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
        </div>
    );
};

export default GroupsPage;
