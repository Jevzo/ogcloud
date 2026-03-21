import { useEffect, useMemo, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    AlertTriangleIcon,
    Clock3Icon,
    LoaderCircleIcon,
    LockIcon,
    RotateCcwIcon,
    ShieldAlertIcon,
    UsersIcon,
} from "lucide-react";
import { Controller, useForm } from "react-hook-form";

import { useNetworkPageContext } from "@/features/network/lib/context";
import {
    createRestartConfirmationCode,
    formatNetworkLockDuration,
    formatNetworkLockType,
    getNetworkLockSummary,
} from "@/features/network/lib/utils";
import {
    networkRestartConfirmationSchema,
    networkServerSettingsFormSchema,
    type NetworkRestartConfirmationValues,
    type NetworkServerSettingsFormValues,
} from "@/features/network/schemas";
import {
    AlertDialog,
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
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
    InputGroupText,
} from "@/components/ui/input-group";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";

const NetworkServerSettingsPage = () => {
    const {
        groups,
        isAdmin,
        isLoading,
        isRestartingNetwork,
        isRefreshing,
        isTogglingMaintenance,
        locks,
        requestNetworkRestart,
        saveSettings,
        setMaintenance,
        settings,
    } = useNetworkPageContext();
    const [isMaintenanceAlertOpen, setIsMaintenanceAlertOpen] = useState(false);
    const [isRestartAlertOpen, setIsRestartAlertOpen] = useState(false);
    const [restartConfirmationCode, setRestartConfirmationCode] = useState("");

    const eligibleDefaultGroups = useMemo(
        () => groups.filter((group) => group.type.toUpperCase() !== "PROXY"),
        [groups],
    );
    const networkRestartLock =
        locks.find((lock) => lock.type.toUpperCase() === "NETWORK_RESTART") ?? null;
    const maintenanceDisableBlocked = Boolean(settings?.maintenance && networkRestartLock);
    const maintenanceSwitchDisabled =
        !settings || isTogglingMaintenance || (settings.maintenance && maintenanceDisableBlocked);
    const restartButtonDisabled =
        !isAdmin ||
        !settings ||
        !settings.maintenance ||
        isRestartingNetwork ||
        Boolean(networkRestartLock);

    const settingsForm = useForm<NetworkServerSettingsFormValues>({
        resolver: zodResolver(networkServerSettingsFormSchema),
        defaultValues: {
            defaultGroup: "",
            maxPlayers: "",
        },
    });
    const restartForm = useForm<NetworkRestartConfirmationValues>({
        resolver: zodResolver(networkRestartConfirmationSchema),
        defaultValues: {
            confirmationCode: "",
        },
    });

    useEffect(() => {
        if (!settings) {
            return;
        }

        const normalizedDefaultGroup = eligibleDefaultGroups.some(
            (group) => group.id === settings.defaultGroup,
        )
            ? settings.defaultGroup
            : "";

        settingsForm.reset({
            defaultGroup: normalizedDefaultGroup,
            maxPlayers: String(settings.maxPlayers),
        });
    }, [eligibleDefaultGroups, settings, settingsForm]);

    const handleSave = settingsForm.handleSubmit(async (values) => {
        const parsedMaxPlayers = Number.parseInt(values.maxPlayers, 10);

        if (!eligibleDefaultGroups.some((group) => group.id === values.defaultGroup)) {
            settingsForm.setError("defaultGroup", {
                message: "Select a non-proxy default group.",
            });
            return;
        }

        try {
            const nextSettings = await saveSettings(
                {
                    defaultGroup: values.defaultGroup,
                    maxPlayers: parsedMaxPlayers,
                },
                "Server settings saved.",
            );

            settingsForm.reset({
                defaultGroup: nextSettings.defaultGroup,
                maxPlayers: String(nextSettings.maxPlayers),
            });
        } catch (error) {
            settingsForm.setError("root", {
                message: error instanceof Error ? error.message : "Unable to save server settings.",
            });
        }
    });

    const handleMaintenanceToggle = async (checked: boolean) => {
        if (!settings || checked === settings.maintenance) {
            return;
        }

        if (checked) {
            setIsMaintenanceAlertOpen(true);
            return;
        }

        if (maintenanceDisableBlocked) {
            return;
        }

        await setMaintenance(false);
    };

    const handleConfirmEnableMaintenance = async () => {
        try {
            await setMaintenance(true);
            setIsMaintenanceAlertOpen(false);
        } catch {
            return;
        }
    };

    const openRestartAlert = () => {
        setRestartConfirmationCode(createRestartConfirmationCode());
        restartForm.reset({
            confirmationCode: "",
        });
        setIsRestartAlertOpen(true);
    };

    const handleRequestRestart = restartForm.handleSubmit(async (values) => {
        if (values.confirmationCode !== restartConfirmationCode) {
            restartForm.setError("confirmationCode", {
                message: "Enter the matching confirmation code.",
            });
            return;
        }

        try {
            await requestNetworkRestart();
            setIsRestartAlertOpen(false);
            restartForm.reset({
                confirmationCode: "",
            });
        } catch (error) {
            restartForm.setError("root", {
                message:
                    error instanceof Error ? error.message : "Unable to request a network restart.",
            });
        }
    });

    if (!settings && !isLoading) {
        return (
            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>Server settings unavailable</CardTitle>
                    <CardDescription>
                        The dashboard could not load capacity, maintenance, and lock data for the
                        network settings view.
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    return (
        <div className="space-y-6">
            <div className="grid gap-6 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
                <Card className="border-border/70 bg-card/80">
                    <CardHeader>
                        <CardTitle>Network operations</CardTitle>
                        <CardDescription>
                            Maintenance mode, network-wide restart flow, and operational safety
                            rails for the live cluster.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="space-y-5">
                        <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                <div className="space-y-2">
                                    <div className="flex items-center gap-2">
                                        <ShieldAlertIcon className="size-4 text-primary" />
                                        <div className="text-sm font-semibold text-foreground">
                                            Maintenance mode
                                        </div>
                                    </div>
                                    <div className="text-sm leading-6 text-muted-foreground">
                                        Enable maintenance before requesting a network restart or
                                        before making disruptive runtime changes.
                                    </div>
                                    {maintenanceDisableBlocked ? (
                                        <div className="text-sm text-amber-300">
                                            Maintenance is currently locked while a network restart
                                            request is still active.
                                        </div>
                                    ) : null}
                                </div>

                                <div className="flex items-center gap-3">
                                    <div className="text-sm font-medium text-foreground">
                                        {settings?.maintenance ? "Enabled" : "Disabled"}
                                    </div>
                                    <Switch
                                        checked={settings?.maintenance ?? false}
                                        onCheckedChange={(checked) => {
                                            void handleMaintenanceToggle(checked);
                                        }}
                                        disabled={maintenanceSwitchDisabled}
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                <div className="space-y-2">
                                    <div className="flex items-center gap-2">
                                        <RotateCcwIcon className="size-4 text-destructive" />
                                        <div className="text-sm font-semibold text-foreground">
                                            Full network restart
                                        </div>
                                    </div>
                                    <div className="text-sm leading-6 text-muted-foreground">
                                        Request the phased restart flow for proxies, default-group
                                        capacity, and the remaining groups.
                                    </div>
                                    {networkRestartLock ? (
                                        <div className="text-sm text-amber-300">
                                            A network restart request is already active.
                                        </div>
                                    ) : null}
                                </div>

                                <Button
                                    type="button"
                                    variant="destructive"
                                    onClick={openRestartAlert}
                                    disabled={restartButtonDisabled}
                                >
                                    {isRestartingNetwork ? (
                                        <>
                                            <LoaderCircleIcon className="size-4 animate-spin" />
                                            Requesting
                                        </>
                                    ) : (
                                        <>
                                            <RotateCcwIcon className="size-4" />
                                            Restart network
                                        </>
                                    )}
                                </Button>
                            </div>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-border/70 bg-card/80">
                    <CardHeader>
                        <div className="flex flex-wrap items-center gap-2">
                            <CardTitle>Active locks</CardTitle>
                            <Badge variant="outline" className="border-border/80">
                                {locks.length} active
                            </Badge>
                        </div>
                        <CardDescription>
                            Synchronization locks currently throttling network restart or
                            permission-related operations.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        {locks.length === 0 ? (
                            <div className="rounded-xl border border-dashed border-border/80 bg-background/45 px-4 py-8 text-sm text-muted-foreground">
                                {isLoading || isRefreshing
                                    ? "Refreshing active lock state..."
                                    : "No active synchronization locks are currently registered."}
                            </div>
                        ) : (
                            <Table>
                                <TableHeader>
                                    <TableRow className="hover:bg-transparent">
                                        <TableHead className="px-4">Lock</TableHead>
                                        <TableHead className="px-4">TTL</TableHead>
                                        <TableHead className="px-4">Target</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {locks.map((lock) => (
                                        <TableRow key={lock.key} className="hover:bg-transparent">
                                            <TableCell className="px-4 py-3 align-top">
                                                <div className="flex items-start gap-3">
                                                    <LockIcon className="mt-0.5 size-4 text-primary" />
                                                    <div className="space-y-1">
                                                        <div className="font-medium text-foreground">
                                                            {formatNetworkLockType(lock.type)}
                                                        </div>
                                                        <div className="text-sm leading-6 text-muted-foreground">
                                                            {getNetworkLockSummary(lock)}
                                                        </div>
                                                        <div className="font-mono text-xs text-muted-foreground">
                                                            {lock.key}
                                                        </div>
                                                    </div>
                                                </div>
                                            </TableCell>
                                            <TableCell className="px-4 py-3">
                                                <div className="inline-flex items-center gap-2 rounded-full border border-border/70 bg-background/70 px-2.5 py-1 text-xs font-medium text-foreground">
                                                    <Clock3Icon className="size-3.5" />
                                                    {formatNetworkLockDuration(lock.ttlSeconds)}
                                                </div>
                                            </TableCell>
                                            <TableCell className="px-4 py-3 text-sm text-muted-foreground">
                                                {lock.targetId ?? "--"}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </CardContent>
                </Card>
            </div>

            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>Server capacity</CardTitle>
                    <CardDescription>
                        Configure the network-wide slot cap and fallback group used when traffic
                        does not target a specific destination.
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-6">
                    <form onSubmit={handleSave} className="space-y-6">
                        <FieldGroup className="grid gap-4 md:grid-cols-2">
                            <Field>
                                <FieldLabel htmlFor="network-max-players">Max players</FieldLabel>
                                <InputGroup>
                                    <InputGroupAddon>
                                        <UsersIcon className="size-4" />
                                    </InputGroupAddon>
                                    <InputGroupInput
                                        id="network-max-players"
                                        type="number"
                                        min={1}
                                        step={1}
                                        inputMode="numeric"
                                        aria-invalid={
                                            settingsForm.formState.errors.maxPlayers
                                                ? "true"
                                                : "false"
                                        }
                                        {...settingsForm.register("maxPlayers", {
                                            onChange: () => settingsForm.clearErrors("root"),
                                        })}
                                    />
                                    <InputGroupAddon align="inline-end">
                                        <InputGroupText>slots</InputGroupText>
                                    </InputGroupAddon>
                                </InputGroup>
                                <FieldDescription>
                                    Total player cap enforced and displayed across the network.
                                </FieldDescription>
                                <FieldError errors={[settingsForm.formState.errors.maxPlayers]} />
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-default-group">
                                    Default group
                                </FieldLabel>
                                <Controller
                                    control={settingsForm.control}
                                    name="defaultGroup"
                                    render={({ field }) => (
                                        <Select
                                            value={field.value}
                                            onValueChange={(value) => {
                                                field.onChange(value);
                                                settingsForm.clearErrors("root");
                                            }}
                                            disabled={
                                                isLoading ||
                                                !settings ||
                                                eligibleDefaultGroups.length === 0
                                            }
                                        >
                                            <SelectTrigger
                                                id="network-default-group"
                                                className="w-full"
                                            >
                                                <SelectValue
                                                    placeholder={
                                                        eligibleDefaultGroups.length === 0
                                                            ? "No eligible groups available"
                                                            : "Select the fallback group"
                                                    }
                                                />
                                            </SelectTrigger>
                                            <SelectContent>
                                                {eligibleDefaultGroups.map((group) => (
                                                    <SelectItem key={group.id} value={group.id}>
                                                        {group.id}
                                                    </SelectItem>
                                                ))}
                                            </SelectContent>
                                        </Select>
                                    )}
                                />
                                <FieldDescription>
                                    Non-proxy group used when routing needs a default gameplay
                                    target.
                                </FieldDescription>
                                <FieldError errors={[settingsForm.formState.errors.defaultGroup]} />
                            </Field>
                        </FieldGroup>

                        <div className="grid gap-3 md:grid-cols-2">
                            <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Current slot cap
                                </div>
                                <div className="mt-2 text-sm font-semibold text-foreground">
                                    {settings?.maxPlayers
                                        ? `${settings.maxPlayers.toLocaleString()} players`
                                        : "--"}
                                </div>
                            </div>
                            <div className="rounded-xl border border-border/70 bg-background/55 p-4">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Current default group
                                </div>
                                <div className="mt-2 text-sm font-semibold text-foreground">
                                    {settings?.defaultGroup ?? "--"}
                                </div>
                            </div>
                        </div>

                        <FieldError>{settingsForm.formState.errors.root?.message}</FieldError>

                        <div className="flex justify-end">
                            <Button type="submit" disabled={settingsForm.formState.isSubmitting}>
                                {settingsForm.formState.isSubmitting ? (
                                    <>
                                        <LoaderCircleIcon className="size-4 animate-spin" />
                                        Saving
                                    </>
                                ) : (
                                    "Save changes"
                                )}
                            </Button>
                        </div>
                    </form>
                </CardContent>
            </Card>

            <AlertDialog open={isMaintenanceAlertOpen} onOpenChange={setIsMaintenanceAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-amber-500/10 text-amber-300">
                            <ShieldAlertIcon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>Enable network maintenance?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Enabling maintenance can remove active players from the network and
                            block normal joins until it is disabled again.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isTogglingMaintenance}>
                            Cancel
                        </AlertDialogCancel>
                        <Button
                            type="button"
                            variant="outline"
                            disabled={isTogglingMaintenance}
                            onClick={() => void handleConfirmEnableMaintenance()}
                        >
                            {isTogglingMaintenance ? "Enabling..." : "Enable maintenance"}
                        </Button>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <AlertDialog
                open={isRestartAlertOpen}
                onOpenChange={(nextOpen) => {
                    if (isRestartingNetwork) {
                        return;
                    }

                    setIsRestartAlertOpen(nextOpen);
                }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-destructive/10 text-destructive">
                            <AlertTriangleIcon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>Request a full network restart?</AlertDialogTitle>
                        <AlertDialogDescription>
                            The backend will restart proxies, default-group capacity, and remaining
                            groups in phases. Type the generated code to confirm the request.
                        </AlertDialogDescription>
                    </AlertDialogHeader>

                    <div className="space-y-4">
                        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive/85">
                            Use this only after confirming maintenance mode is enabled and player
                            traffic can be safely interrupted.
                        </div>

                        <div className="rounded-xl border border-border/70 bg-muted/35 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Confirmation code
                            </div>
                            <div className="mt-2 font-mono text-2xl font-semibold tracking-[0.35em] text-foreground">
                                {restartConfirmationCode}
                            </div>
                        </div>

                        <Field>
                            <FieldLabel htmlFor="network-restart-confirmation-code">
                                Type the 6-digit code
                            </FieldLabel>
                            <InputGroup>
                                <InputGroupInput
                                    id="network-restart-confirmation-code"
                                    className="font-mono tracking-[0.28em]"
                                    inputMode="numeric"
                                    maxLength={6}
                                    autoFocus
                                    aria-invalid={
                                        restartForm.formState.errors.confirmationCode
                                            ? "true"
                                            : "false"
                                    }
                                    {...restartForm.register("confirmationCode", {
                                        onChange: (event) => {
                                            event.target.value = event.target.value
                                                .replace(/\D/g, "")
                                                .slice(0, 6);
                                            restartForm.clearErrors("root");
                                        },
                                    })}
                                />
                            </InputGroup>
                            <FieldDescription>
                                This confirmation step reduces accidental network-wide restart
                                requests.
                            </FieldDescription>
                            <FieldError errors={[restartForm.formState.errors.confirmationCode]} />
                        </Field>

                        <FieldError>{restartForm.formState.errors.root?.message}</FieldError>
                    </div>

                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isRestartingNetwork}>Cancel</AlertDialogCancel>
                        <Button
                            type="button"
                            variant="destructive"
                            disabled={isRestartingNetwork}
                            onClick={() => void handleRequestRestart()}
                        >
                            {isRestartingNetwork ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Requesting
                                </>
                            ) : (
                                "Request restart"
                            )}
                        </Button>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </div>
    );
};

export default NetworkServerSettingsPage;
