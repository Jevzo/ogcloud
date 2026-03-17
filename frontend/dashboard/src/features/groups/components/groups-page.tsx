import { useDeferredValue, useEffect, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    Layers3Icon,
    LoaderCircleIcon,
    PlusIcon,
    SearchIcon,
    ShieldAlertIcon,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router";
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
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
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
import GroupConfigurationForm from "@/features/groups/components/group-configuration-form";
import { useGroupFormOptionsQuery } from "@/features/groups/hooks/use-group-form-options-query";
import { useGroupsQuery } from "@/features/groups/hooks/use-groups-query";
import { groupFormSchema } from "@/features/groups/schemas";
import { useAccessToken } from "@/hooks/use-access-token";
import { createServerGroup } from "@/lib/api";
import { buildCreateGroupPayload, createEmptyGroupValues } from "@/lib/group-form";
import { getRuntimeProfileLabel } from "@/lib/group-runtime";
import { formatDateTime } from "@/lib/server-display";
import type { GroupFormValues, GroupRecord } from "@/types/group";
import type { TemplateRecord } from "@/types/template";

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

const GroupsPageSkeleton = () => (
    <div className="space-y-4">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {Array.from({ length: 4 }).map((_, index) => (
                <Card key={`group-summary-skeleton-${index}`} className="border border-border/70 bg-card/85">
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
            <CardContent className="space-y-3">
                <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto]">
                    <Skeleton className="h-8 w-full" />
                    <Skeleton className="h-8 w-32" />
                </div>
                {Array.from({ length: 6 }).map((_, index) => (
                    <Skeleton key={`groups-table-skeleton-${index}`} className="h-12 w-full" />
                ))}
            </CardContent>
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

    const [searchInput, setSearchInput] = useState("");
    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);

    const deferredQuery = useDeferredValue(searchInput.trim());
    const {
        data: groups,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
        refreshIntervalMs,
    } = useGroupsQuery({
        query: deferredQuery,
    });
    const {
        data: templates,
        errorMessage: templatesErrorMessage,
    } = useGroupFormOptionsQuery();
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
            const createdGroup = await createServerGroup(accessToken, buildCreateGroupPayload(values));

            toast.success(`Created server group ${createdGroup.id}.`);
            handleCreateDialogChange(false);
            await refresh(false);
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to create group.";

            form.setError("root", {
                message,
            });
            toast.error(message);
        }
    });

    const maintenanceCount = groups.filter((group) => group.maintenance).length;
    const staticCount = groups.filter((group) => group.type === "STATIC").length;
    const proxyCount = groups.filter((group) => group.type === "PROXY").length;
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
                    <Badge variant="outline" className="w-fit border-primary/25 bg-primary/10 text-primary">
                        Runtime groups
                    </Badge>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            Server groups
                        </h1>
                        <p className="max-w-3xl text-sm text-muted-foreground">
                            Manage runtime templates, autoscaling posture, and resource envelopes
                            for every deployable group.
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
                    value={`${groups.length}`}
                    helper={
                        deferredQuery
                            ? "Groups matching the active search query."
                            : "All deployable groups currently visible to the dashboard."
                    }
                />
                <SummaryCard
                    label="Maintenance"
                    value={`${maintenanceCount}`}
                    helper="Groups currently held out of normal player traffic."
                />
                <SummaryCard
                    label="Static storage"
                    value={`${staticCount}`}
                    helper="Persistent groups retaining attached storage volumes."
                />
                <SummaryCard
                    label="Proxy entrypoints"
                    value={`${proxyCount}`}
                    helper="Velocity-facing groups handling ingress or routing."
                />
            </div>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Group inventory
                            </CardDescription>
                            <CardTitle className="text-base">Deployable runtime groups</CardTitle>
                            <CardDescription>
                                Search across group IDs, inspect runtime posture, and open the
                                full configuration workflow for changes.
                            </CardDescription>
                        </div>
                        <CardAction className="col-auto row-auto">
                            <div className="flex flex-wrap items-center gap-2">
                                <Button
                                    variant="outline"
                                    onClick={() => void refresh()}
                                    disabled={isRefreshing}
                                >
                                    <LoaderCircleIcon
                                        className={isRefreshing ? "size-4 animate-spin" : "size-4"}
                                    />
                                    Refresh
                                </Button>
                                <Button onClick={() => setIsCreateDialogOpen(true)}>
                                    <PlusIcon className="size-4" />
                                    Create group
                                </Button>
                            </div>
                        </CardAction>
                    </div>

                    <InputGroup>
                        <InputGroupAddon>
                            <SearchIcon className="size-4" />
                        </InputGroupAddon>
                        <InputGroupInput
                            value={searchInput}
                            onChange={(event) => setSearchInput(event.target.value)}
                            placeholder="Search group ID, type, runtime, template, or image"
                        />
                    </InputGroup>
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
                                <TableHead className="px-4 text-right">Open</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {groups.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={8}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No groups matched the current filters.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                groups.map((group) => (
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
                                                    <Badge
                                                        variant="outline"
                                                        className={getTypeBadgeClassName(group.type)}
                                                    >
                                                        {group.type}
                                                    </Badge>
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
                                            <Badge
                                                variant="outline"
                                                className={
                                                    group.maintenance
                                                        ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                                        : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                }
                                            >
                                                {group.maintenance ? "Enabled" : "Disabled"}
                                            </Badge>
                                        </TableCell>
                                        <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                            {formatDateTime(group.updatedAt)}
                                        </TableCell>
                                        <TableCell
                                            className="px-4 py-3 text-right align-top"
                                            onClick={(event) => event.stopPropagation()}
                                        >
                                            <Button variant="ghost" size="sm" asChild>
                                                <Link to={`/groups/${encodeURIComponent(group.id)}`}>
                                                    Open
                                                </Link>
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </CardContent>
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
                        <GroupConfigurationForm form={form} templates={templates} />

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
