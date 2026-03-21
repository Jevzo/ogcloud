import { useEffect, useMemo, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    AlertTriangleIcon,
    ArrowLeftIcon,
    Layers3Icon,
    LoaderCircleIcon,
    RotateCcwIcon,
    ShieldAlertIcon,
    ShieldCheckIcon,
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
import { FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import GroupConfigurationForm from "@/features/groups/components/group-configuration-form";
import { useGroupDetailsQuery } from "@/features/groups/hooks/use-group-details-query";
import { useGroupFormOptionsQuery } from "@/features/groups/hooks/use-group-form-options-query";
import { groupFormSchema } from "@/features/groups/schemas";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import {
    deleteServerGroup,
    restartServerGroup,
    toggleServerGroupMaintenance,
    updateServerGroup,
} from "@/api";
import {
    buildUpdateGroupPayload,
    createEmptyGroupValues,
    toGroupFormValues,
} from "@/features/groups/lib/group-form";
import { getRuntimeProfileLabel } from "@/features/groups/lib/group-runtime";
import { formatDateTime } from "@/features/servers/lib/server-display";
import type { GroupFormValues } from "@/types/group";

const createRestartConfirmationCode = () => `${Math.floor(100000 + Math.random() * 900000)}`;

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

const DetailRow = ({ label, value }: { label: string; value: string }) => (
    <div className="flex flex-col gap-1 rounded-xl border border-border/70 bg-background/45 px-4 py-3">
        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">{label}</div>
        <div className="break-all text-sm font-medium text-foreground">{value}</div>
    </div>
);

const GroupDetailsSkeleton = () => (
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
                    key={`group-detail-stat-${index}`}
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

        <div className="grid gap-4 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
            <Card className="border border-border/70 bg-card/85">
                <CardHeader>
                    <Skeleton className="h-6 w-40" />
                </CardHeader>
                <CardContent className="grid gap-3 md:grid-cols-2">
                    {Array.from({ length: 6 }).map((_, index) => (
                        <Skeleton key={`group-detail-row-${index}`} className="h-18 w-full" />
                    ))}
                </CardContent>
            </Card>

            <Card className="border border-border/70 bg-card/85">
                <CardHeader>
                    <Skeleton className="h-6 w-48" />
                </CardHeader>
                <CardContent className="space-y-4">
                    {Array.from({ length: 5 }).map((_, index) => (
                        <Skeleton key={`group-form-skeleton-${index}`} className="h-28 w-full" />
                    ))}
                </CardContent>
            </Card>
        </div>
    </div>
);

const GroupDetailsPage = () => {
    const params = useParams();
    const navigate = useNavigate();
    const getAccessToken = useAccessToken();
    const groupName = decodeURIComponent(params.groupName ?? "");
    const { currentOnlineCount, errorMessage, group, isLoading, refresh } =
        useGroupDetailsQuery(groupName);
    const { data: templates, errorMessage: templatesErrorMessage } = useGroupFormOptionsQuery();
    const form = useForm<GroupFormValues>({
        resolver: zodResolver(groupFormSchema),
        defaultValues: createEmptyGroupValues(),
    });

    const [isRestartDialogOpen, setIsRestartDialogOpen] = useState(false);
    const [restartConfirmationCode, setRestartConfirmationCode] = useState("");
    const [restartConfirmationInput, setRestartConfirmationInput] = useState("");
    const [restartErrorMessage, setRestartErrorMessage] = useState<string | null>(null);
    const [isRestartingGroup, setIsRestartingGroup] = useState(false);
    const [isMaintenanceAlertOpen, setIsMaintenanceAlertOpen] = useState(false);
    const [isTogglingMaintenance, setIsTogglingMaintenance] = useState(false);
    const [isDeleteAlertOpen, setIsDeleteAlertOpen] = useState(false);
    const [isDeletingGroup, setIsDeletingGroup] = useState(false);

    useEffect(() => {
        if (!group) {
            return;
        }

        form.reset(toGroupFormValues(group));
    }, [form, group]);

    const handleSaveGroup = form.handleSubmit(async (values) => {
        if (!group) {
            return;
        }

        try {
            const accessToken = await getAccessToken();
            const updatedGroup = await updateServerGroup(
                accessToken,
                group.id,
                buildUpdateGroupPayload(values),
            );

            toast.success(`Updated group ${updatedGroup.id}.`);
            await refresh(false);
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to update group.";

            form.setError("root", {
                message,
            });
            toast.error(message);
        }
    });

    const handleDisableMaintenance = async () => {
        if (!group) {
            return;
        }

        setIsTogglingMaintenance(true);

        try {
            const accessToken = await getAccessToken();
            const updatedGroup = await toggleServerGroupMaintenance(accessToken, group.id, false);

            toast.success(`${updatedGroup.id} maintenance disabled.`);
            await refresh(false);
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to update maintenance mode.",
            );
        } finally {
            setIsTogglingMaintenance(false);
        }
    };

    const handleEnableMaintenance = async () => {
        if (!group) {
            return;
        }

        setIsTogglingMaintenance(true);

        try {
            const accessToken = await getAccessToken();
            const updatedGroup = await toggleServerGroupMaintenance(accessToken, group.id, true);

            toast.success(`${updatedGroup.id} maintenance enabled.`);
            setIsMaintenanceAlertOpen(false);
            await refresh(false);
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to update maintenance mode.",
            );
        } finally {
            setIsTogglingMaintenance(false);
        }
    };

    const handleDeleteGroup = async () => {
        if (!group) {
            return;
        }

        setIsDeletingGroup(true);

        try {
            const accessToken = await getAccessToken();

            await deleteServerGroup(accessToken, group.id);
            toast.success(`Deleted group ${group.id}.`);
            navigate("/groups", { replace: true });
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to delete group.");
        } finally {
            setIsDeletingGroup(false);
            setIsDeleteAlertOpen(false);
        }
    };

    const openRestartDialog = () => {
        if (!group?.maintenance) {
            toast.error("Enable group maintenance before requesting a restart.");
            return;
        }

        setRestartConfirmationCode(createRestartConfirmationCode());
        setRestartConfirmationInput("");
        setRestartErrorMessage(null);
        setIsRestartDialogOpen(true);
    };

    const handleRestartGroup = async () => {
        if (!group) {
            return;
        }

        if (restartConfirmationInput.trim() !== restartConfirmationCode) {
            setRestartErrorMessage("Enter the generated 6-digit code to confirm the restart.");
            return;
        }

        setIsRestartingGroup(true);
        setRestartErrorMessage(null);

        try {
            const accessToken = await getAccessToken();

            await restartServerGroup(accessToken, group.id);
            toast.success(`${group.id} restart requested.`);
            setIsRestartDialogOpen(false);
            await refresh(false);
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to restart group.";

            setRestartErrorMessage(message);
            toast.error(message);
        } finally {
            setIsRestartingGroup(false);
        }
    };

    const overviewDetails = useMemo(
        () =>
            group
                ? [
                      ["Template bucket", group.templateBucket],
                      ["Template", `${group.templatePath} / ${group.templateVersion}`],
                      ["Server image", group.serverImage],
                      ["JVM flags", group.jvmFlags],
                      ["Created", formatDateTime(group.createdAt)],
                      ["Last updated", formatDateTime(group.updatedAt)],
                  ]
                : [],
        [group],
    );

    const scalingDetails = useMemo(
        () =>
            group
                ? [
                      ["Min online", `${group.scaling.minOnline}`],
                      ["Max instances", `${group.scaling.maxInstances}`],
                      ["Players per server", `${group.scaling.playersPerServer}`],
                      ["Scale-up threshold", `${group.scaling.scaleUpThreshold}`],
                      ["Scale-down threshold", `${group.scaling.scaleDownThreshold}`],
                      ["Cooldown", `${group.scaling.cooldownSeconds}s`],
                  ]
                : [],
        [group],
    );

    if (isLoading && !group) {
        return <GroupDetailsSkeleton />;
    }

    if (errorMessage && !group) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Group Error
                    </CardDescription>
                    <CardTitle className="text-destructive">Unable to load this group</CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
                <CardFooter className="justify-between gap-3">
                    <Button variant="outline" asChild>
                        <Link to="/groups">
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
                        <Link to="/groups">
                            <ArrowLeftIcon className="size-4" />
                            Back to groups
                        </Link>
                    </Button>

                    <div className="flex flex-wrap items-center gap-2 xl:justify-end">
                        <Button
                            variant="outline"
                            onClick={openRestartDialog}
                            disabled={!group || !group.maintenance || isRestartingGroup}
                        >
                            <RotateCcwIcon className="size-4" />
                            Restart group
                        </Button>
                        <Button
                            variant="outline"
                            onClick={() =>
                                group?.maintenance
                                    ? void handleDisableMaintenance()
                                    : setIsMaintenanceAlertOpen(true)
                            }
                            disabled={!group || isTogglingMaintenance}
                        >
                            {group?.maintenance ? (
                                <ShieldCheckIcon className="size-4" />
                            ) : (
                                <ShieldAlertIcon className="size-4" />
                            )}
                            {group?.maintenance ? "Disable maintenance" : "Enable maintenance"}
                        </Button>
                        <Button
                            variant="destructive"
                            onClick={() => setIsDeleteAlertOpen(true)}
                            disabled={!group}
                        >
                            <Trash2Icon className="size-4" />
                            Delete group
                        </Button>
                    </div>
                </div>

                <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                        {group ? (
                            <Badge
                                variant="outline"
                                className={
                                    group.type === "STATIC"
                                        ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                        : group.type === "PROXY"
                                          ? "border-sky-500/30 bg-sky-500/10 text-sky-300"
                                          : "border-primary/30 bg-primary/10 text-primary"
                                }
                            >
                                {group.type}
                            </Badge>
                        ) : null}
                        {group ? (
                            <Badge
                                variant="outline"
                                className={
                                    group.maintenance
                                        ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                        : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                }
                            >
                                {group.maintenance ? "Maintenance enabled" : "Traffic open"}
                            </Badge>
                        ) : null}
                    </div>

                    <div>
                        <CardTitle className="flex items-center gap-2 text-2xl tracking-tight">
                            <Layers3Icon className="size-5 text-primary" />
                            {group?.id ?? groupName}
                        </CardTitle>
                        <CardDescription className="mt-2 max-w-3xl text-sm leading-6">
                            Template settings, autoscaling thresholds, resources, and lifecycle
                            controls for the selected runtime group.
                        </CardDescription>
                    </div>
                </div>
            </div>

            {errorMessage && group ? (
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
                        <CardTitle className="text-base text-amber-200">
                            Template catalog is unavailable
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {templatesErrorMessage}. Existing values are preserved, and you can
                            still edit template path and version directly.
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <StatCard
                    label="Runtime"
                    value={group ? getRuntimeProfileLabel(group.runtimeProfile) : "--"}
                    helper="Managed runtime profile or proxy runtime selected for this group."
                />
                <StatCard
                    label="Online now"
                    value={currentOnlineCount !== null ? `${currentOnlineCount}` : "--"}
                    helper="Running instances currently returned from the server inventory API."
                />
                <StatCard
                    label="Scaling cap"
                    value={group ? `${group.scaling.maxInstances}` : "--"}
                    helper="Maximum number of instances allowed at one time."
                />
                <StatCard
                    label="Players / server"
                    value={group ? `${group.scaling.playersPerServer}` : "--"}
                    helper="Target player density used for balancing and autoscaling."
                />
            </div>

            <Tabs defaultValue="overview" className="gap-4">
                <TabsList variant="line">
                    <TabsTrigger value="overview">Overview</TabsTrigger>
                    <TabsTrigger value="configuration">Runtime configuration</TabsTrigger>
                </TabsList>

                <TabsContent value="overview" className="space-y-4">
                    <div className="space-y-4">
                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader className="pb-4">
                                <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                    Overview
                                </CardDescription>
                                <CardTitle className="text-base">
                                    Template and runtime posture
                                </CardTitle>
                            </CardHeader>
                            <CardContent className="grid gap-3 md:grid-cols-2">
                                {overviewDetails.map(([label, value]) => (
                                    <DetailRow key={label} label={label} value={value} />
                                ))}
                            </CardContent>
                        </Card>

                        <div className="grid gap-4 xl:grid-cols-2">
                            <Card className="border border-border/70 bg-card/85 shadow-none">
                                <CardHeader className="pb-4">
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Scaling
                                    </CardDescription>
                                    <CardTitle className="text-base">
                                        Autoscaling snapshot
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="grid gap-3 md:grid-cols-2">
                                    {scalingDetails.map(([label, value]) => (
                                        <DetailRow key={label} label={label} value={value} />
                                    ))}
                                </CardContent>
                            </Card>

                            <Card className="border border-border/70 bg-card/85 shadow-none">
                                <CardHeader className="pb-4">
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Resources
                                    </CardDescription>
                                    <CardTitle className="text-base">Kubernetes envelope</CardTitle>
                                </CardHeader>
                                <CardContent className="grid gap-3 md:grid-cols-2">
                                    <DetailRow
                                        label="Memory request"
                                        value={group?.resources.memoryRequest ?? "--"}
                                    />
                                    <DetailRow
                                        label="Memory limit"
                                        value={group?.resources.memoryLimit ?? "--"}
                                    />
                                    <DetailRow
                                        label="CPU request"
                                        value={group?.resources.cpuRequest ?? "--"}
                                    />
                                    <DetailRow
                                        label="CPU limit"
                                        value={group?.resources.cpuLimit ?? "--"}
                                    />
                                    <DetailRow
                                        label="Storage size"
                                        value={group?.storageSize || "Not applicable"}
                                    />
                                    <DetailRow
                                        label="Maintenance"
                                        value={group?.maintenance ? "Enabled" : "Disabled"}
                                    />
                                </CardContent>
                            </Card>
                        </div>
                    </div>
                </TabsContent>

                <TabsContent value="configuration">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader className="border-b border-border/70 pb-4">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                                <div>
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Editable configuration
                                    </CardDescription>
                                    <CardTitle className="text-base">
                                        Runtime, scaling, and resources
                                    </CardTitle>
                                    <CardDescription>
                                        Identity fields stay locked here so deployment semantics
                                        remain stable while editing the rest of the group.
                                    </CardDescription>
                                </div>
                                {form.formState.isDirty ? (
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
                                <GroupConfigurationForm
                                    disableIdentityFields
                                    form={form}
                                    lockTemplateFieldsToKnownSelection
                                    templates={templates}
                                />

                                <FieldError>{form.formState.errors.root?.message}</FieldError>
                            </CardContent>
                            <CardFooter className="justify-end gap-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => group && form.reset(toGroupFormValues(group))}
                                    disabled={
                                        !group ||
                                        form.formState.isSubmitting ||
                                        !form.formState.isDirty
                                    }
                                >
                                    Reset
                                </Button>
                                <Button
                                    type="submit"
                                    disabled={
                                        !group ||
                                        form.formState.isSubmitting ||
                                        !form.formState.isDirty
                                    }
                                >
                                    {form.formState.isSubmitting ? (
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
                </TabsContent>
            </Tabs>

            <Dialog
                open={isRestartDialogOpen}
                onOpenChange={(nextOpen) => {
                    if (isRestartingGroup) {
                        return;
                    }

                    setIsRestartDialogOpen(nextOpen);
                }}
            >
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <RotateCcwIcon className="size-4 text-destructive" />
                            Restart group
                        </DialogTitle>
                        <DialogDescription>
                            This requests a phased restart for {group?.id ?? groupName}. Type the
                            generated code to confirm the action.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4">
                        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive/85">
                            The backend will restart active servers in this group. Use this only
                            after you have confirmed maintenance mode is enabled.
                        </div>

                        <div className="rounded-xl border border-border/70 bg-muted/35 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Confirmation code
                            </div>
                            <div className="mt-2 font-mono text-2xl font-semibold tracking-[0.35em] text-foreground">
                                {restartConfirmationCode}
                            </div>
                        </div>

                        <div className="space-y-2">
                            <FieldLabel htmlFor="restart-confirmation-input">
                                Type the 6-digit code
                            </FieldLabel>
                            <Input
                                id="restart-confirmation-input"
                                value={restartConfirmationInput}
                                onChange={(event) =>
                                    setRestartConfirmationInput(
                                        event.target.value.replace(/\D/g, "").slice(0, 6),
                                    )
                                }
                                className="font-mono tracking-[0.28em]"
                                inputMode="numeric"
                                maxLength={6}
                                autoFocus
                            />
                            <FieldError>{restartErrorMessage}</FieldError>
                        </div>
                    </div>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setIsRestartDialogOpen(false)}
                            disabled={isRestartingGroup}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            variant="destructive"
                            onClick={() => void handleRestartGroup()}
                            disabled={
                                isRestartingGroup || restartConfirmationInput.trim().length !== 6
                            }
                        >
                            {isRestartingGroup ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Requesting
                                </>
                            ) : (
                                "Request restart"
                            )}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>

            <AlertDialog open={isMaintenanceAlertOpen} onOpenChange={setIsMaintenanceAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-amber-500/10 text-amber-300">
                            <ShieldAlertIcon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>
                            Enable maintenance for {group?.id ?? groupName}?
                        </AlertDialogTitle>
                        <AlertDialogDescription>
                            Enabling maintenance can disconnect active players from this group and
                            block new joins until it is disabled again.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isTogglingMaintenance}>
                            Cancel
                        </AlertDialogCancel>
                        <AlertDialogAction
                            variant="outline"
                            disabled={isTogglingMaintenance}
                            onClick={() => void handleEnableMaintenance()}
                        >
                            {isTogglingMaintenance ? "Enabling..." : "Enable maintenance"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog open={isDeleteAlertOpen} onOpenChange={setIsDeleteAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-destructive/10 text-destructive">
                            <AlertTriangleIcon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>Delete {group?.id ?? groupName}?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Existing servers in this group may fail to deploy or scale after this
                            action. Delete the group only after traffic and automation no longer
                            depend on it.
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

export default GroupDetailsPage;
