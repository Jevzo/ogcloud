import { zodResolver } from "@hookform/resolvers/zod";
import {
    Clock3Icon,
    FingerprintIcon,
    LoaderCircleIcon,
    ServerIcon,
    ShieldCheckIcon,
    ShuffleIcon,
    UserRoundIcon,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { usePlayerManagementOptionsQuery } from "@/features/players/hooks/use-player-management-options-query";
import {
    playerPermissionAssignmentFormSchema,
    playerTransferFormSchema,
    type PlayerPermissionAssignmentFormValues,
    type PlayerTransferFormValues,
} from "@/features/players/schemas";
import { useAccessToken } from "@/hooks/use-access-token";
import { transferPlayerToTarget, updatePlayerPermissionGroup } from "@/lib/api";
import { hasAdminAccess } from "@/lib/roles";
import { formatDateTime } from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import type { PersistedPlayerRecord, PlayerRecord } from "@/types/player";

interface PlayerManagementDialogProps {
    onClose: () => void;
    onPlayerUpdated?: (player: PersistedPlayerRecord) => void;
    onTransferComplete?: () => void | Promise<void>;
    player: PersistedPlayerRecord | null;
}

const getPlayerStatusBadgeClassName = (online: boolean) =>
    online
        ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
        : "border-border/80 text-muted-foreground";

const formatPermissionExpiry = (endMillis: number) => {
    if (endMillis === -1) {
        return "Permanent";
    }

    if (!Number.isFinite(endMillis) || endMillis <= 0) {
        return "--";
    }

    return formatDateTime(String(endMillis));
};

const SummaryItem = ({
    helper,
    icon: Icon,
    label,
    value,
}: {
    helper: string;
    icon: typeof Clock3Icon;
    label: string;
    value: string;
}) => (
    <div className="rounded-xl border border-border/70 bg-background/40 p-3">
        <div className="flex items-start gap-3">
            <div className="rounded-lg border border-primary/20 bg-primary/10 p-2 text-primary">
                <Icon className="size-4" />
            </div>
            <div className="min-w-0 space-y-1">
                <div className="text-xs uppercase tracking-[0.24em] text-muted-foreground">
                    {label}
                </div>
                <div className="truncate text-sm font-medium text-foreground">{value}</div>
                <div className="text-xs text-muted-foreground">{helper}</div>
            </div>
        </div>
    </div>
);

const PlayerManagementDialog = ({
    onClose,
    onPlayerUpdated,
    onTransferComplete,
    player,
}: PlayerManagementDialogProps) => {
    const getAccessToken = useAccessToken();
    const session = useAuthStore((state) => state.session);
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const canManagePermissionGroups = hasAdminAccess(session?.user.role);

    const [activePlayer, setActivePlayer] = useState<PlayerRecord | null>(player);
    const [isUpdatingPermission, setIsUpdatingPermission] = useState(false);
    const [isTransferringPlayer, setIsTransferringPlayer] = useState(false);
    const [isTransferringLinkedAccount, setIsTransferringLinkedAccount] = useState(false);

    const isBusy = isUpdatingPermission || isTransferringPlayer || isTransferringLinkedAccount;
    const linkedPlayerUuid = session?.user.linkedPlayerUuid?.trim() ?? "";

    const permissionForm = useForm<PlayerPermissionAssignmentFormValues>({
        resolver: zodResolver(playerPermissionAssignmentFormSchema),
        defaultValues: {
            duration: "-1",
            group: player?.permission.group ?? "",
        },
    });
    const transferForm = useForm<PlayerTransferFormValues>({
        resolver: zodResolver(playerTransferFormSchema),
        defaultValues: {
            target: "",
        },
    });

    const {
        errorMessage: optionsErrorMessage,
        isLoading: isLoadingOptions,
        permissionGroups,
        refresh: refreshOptions,
        transferTargets,
    } = usePlayerManagementOptionsQuery({
        canManagePermissionGroups,
        enabled: Boolean(player),
        permissionSystemEnabled,
    });

    useEffect(() => {
        setActivePlayer(player);
        permissionForm.reset({
            duration: "-1",
            group: player?.permission.group ?? "",
        });
        transferForm.reset({ target: "" });
    }, [permissionForm, player, transferForm]);

    const availableTransferTargets = useMemo(
        () =>
            transferTargets.filter(
                (server) => !activePlayer?.serverId || server.id !== activePlayer.serverId,
            ),
        [activePlayer?.serverId, transferTargets],
    );

    const closeDialog = (force = false) => {
        if (!force && isBusy) {
            return;
        }

        onClose();
    };

    const handlePermissionSubmit = permissionForm.handleSubmit(async (values) => {
        if (!activePlayer) {
            return;
        }

        if (!permissionSystemEnabled) {
            toast.error("Permission system is disabled in network settings.");
            return;
        }

        setIsUpdatingPermission(true);

        try {
            const accessToken = await getAccessToken();
            const updatedPlayer = await updatePlayerPermissionGroup(
                accessToken,
                activePlayer.uuid,
                values.group.trim(),
                values.duration.trim(),
            );

            setActivePlayer(updatedPlayer);
            permissionForm.reset({
                duration: "-1",
                group: updatedPlayer.permission.group,
            });
            onPlayerUpdated?.(updatedPlayer);
            toast.success(`Updated ${updatedPlayer.name} to ${updatedPlayer.permission.group}.`);
        } catch (error) {
            toast.error(
                error instanceof Error
                    ? error.message
                    : "Unable to update this player's permission group.",
            );
        } finally {
            setIsUpdatingPermission(false);
        }
    });

    const handleTransferSubmit = transferForm.handleSubmit(async (values) => {
        if (!activePlayer) {
            return;
        }

        if (!activePlayer.online) {
            toast.error("Only online players can be moved to another server.");
            return;
        }

        setIsTransferringPlayer(true);

        try {
            const accessToken = await getAccessToken();
            await transferPlayerToTarget(accessToken, activePlayer.uuid, values.target.trim());
            await onTransferComplete?.();
            toast.success(`Transfer requested for ${activePlayer.name}.`);
            closeDialog(true);
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to move this player.",
            );
        } finally {
            setIsTransferringPlayer(false);
        }
    });

    const handleTransferLinkedAccount = async () => {
        if (!activePlayer) {
            return;
        }

        if (!linkedPlayerUuid) {
            toast.error("Your web account is not linked to Minecraft.");
            return;
        }

        if (!activePlayer.online || !activePlayer.serverId) {
            toast.error("This player is not connected to a target game server right now.");
            return;
        }

        if (linkedPlayerUuid === activePlayer.uuid) {
            toast.error("You are already this player.");
            return;
        }

        setIsTransferringLinkedAccount(true);

        try {
            const accessToken = await getAccessToken();
            await transferPlayerToTarget(accessToken, linkedPlayerUuid, activePlayer.serverId);
            await onTransferComplete?.();
            toast.success(`Transfer requested: you -> ${activePlayer.serverId}.`);
            closeDialog(true);
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to transfer your linked account.",
            );
        } finally {
            setIsTransferringLinkedAccount(false);
        }
    };

    if (!activePlayer) {
        return null;
    }

    return (
        <Dialog
            open={Boolean(activePlayer)}
            onOpenChange={(open) => {
                if (!open) {
                    closeDialog();
                }
            }}
        >
            <DialogContent
                className="max-h-[92vh] gap-0 overflow-hidden p-0 sm:max-w-4xl"
                onEscapeKeyDown={(event) => {
                    if (isBusy) {
                        event.preventDefault();
                    }
                }}
                onInteractOutside={(event) => {
                    if (isBusy) {
                        event.preventDefault();
                    }
                }}
            >
                <DialogHeader className="border-b border-border/70 px-6 py-5">
                    <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className="border-primary/25 bg-primary/10 text-primary">
                            Player operations
                        </Badge>
                        <Badge
                            variant="outline"
                            className={getPlayerStatusBadgeClassName(activePlayer.online)}
                        >
                            {activePlayer.online ? "Online" : "Offline"}
                        </Badge>
                    </div>
                    <DialogTitle className="text-xl">Manage {activePlayer.name}</DialogTitle>
                    <DialogDescription className="max-w-2xl">
                        Review identity, update permission groups, and move live sessions between
                        running servers without leaving the current screen.
                    </DialogDescription>
                </DialogHeader>

                <div className="grid flex-1 gap-4 overflow-y-auto px-6 py-5 lg:grid-cols-[320px_minmax(0,1fr)]">
                    <div className="space-y-4">
                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader>
                                <CardTitle className="text-base">Identity</CardTitle>
                                <CardDescription>
                                    Persisted player record and current routing targets.
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-3">
                                <SummaryItem
                                    helper="Persisted UUID used for player-specific moderation actions."
                                    icon={FingerprintIcon}
                                    label="UUID"
                                    value={activePlayer.uuid}
                                />
                                <SummaryItem
                                    helper="Permission expiry reflected on the current record."
                                    icon={ShieldCheckIcon}
                                    label="Permission"
                                    value={`${activePlayer.permission.group} · ${formatPermissionExpiry(activePlayer.permission.endMillis)}`}
                                />
                                <SummaryItem
                                    helper="Current proxy and server placement if the player is online."
                                    icon={ServerIcon}
                                    label="Route"
                                    value={`${activePlayer.proxyId ?? "--"} -> ${activePlayer.serverId ?? "--"}`}
                                />
                                <SummaryItem
                                    helper="First join and most recent live session timestamps."
                                    icon={Clock3Icon}
                                    label="Session"
                                    value={`${formatDateTime(activePlayer.firstJoin)} / ${formatDateTime(activePlayer.connectedAt)}`}
                                />
                            </CardContent>
                        </Card>

                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader>
                                <CardTitle className="text-base">Current state</CardTitle>
                                <CardDescription>
                                    Immediate details needed before applying a permission or
                                    transfer action.
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-2 text-sm text-muted-foreground">
                                <div className="flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-background/40 px-3 py-2">
                                    <span className="flex items-center gap-2">
                                        <UserRoundIcon className="size-4 text-primary" />
                                        Display name
                                    </span>
                                    <span className="font-medium text-foreground">
                                        {activePlayer.name}
                                    </span>
                                </div>
                                <div className="flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-background/40 px-3 py-2">
                                    <span>Connected now</span>
                                    <span className="font-medium text-foreground">
                                        {activePlayer.online ? "Yes" : "No"}
                                    </span>
                                </div>
                                <div className="flex items-center justify-between gap-3 rounded-lg border border-border/70 bg-background/40 px-3 py-2">
                                    <span>Linked account transfer</span>
                                    <span className="font-medium text-foreground">
                                        {linkedPlayerUuid ? "Available" : "Unavailable"}
                                    </span>
                                </div>
                            </CardContent>
                        </Card>
                    </div>

                    <div className="space-y-4">
                        {optionsErrorMessage ? (
                            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                                <CardHeader>
                                    <CardTitle className="text-base text-destructive">
                                        Unable to load player actions
                                    </CardTitle>
                                    <CardDescription className="text-destructive/80">
                                        {optionsErrorMessage}
                                    </CardDescription>
                                </CardHeader>
                                <CardContent>
                                    <Button
                                        variant="outline"
                                        onClick={() => void refreshOptions()}
                                        disabled={isLoadingOptions}
                                    >
                                        {isLoadingOptions ? (
                                            <LoaderCircleIcon className="size-4 animate-spin" />
                                        ) : null}
                                        Retry
                                    </Button>
                                </CardContent>
                            </Card>
                        ) : null}

                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader>
                                <CardTitle className="flex items-center gap-2 text-base">
                                    <ShieldCheckIcon className="size-4 text-primary" />
                                    Permission group
                                </CardTitle>
                                <CardDescription>
                                    Apply a permanent or temporary permission group to this player.
                                </CardDescription>
                            </CardHeader>
                            <CardContent>
                                <form className="space-y-4" onSubmit={(event) => void handlePermissionSubmit(event)}>
                                    <FieldGroup className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_200px]">
                                        <Field>
                                            <FieldLabel htmlFor="player-permission-group">
                                                Permission group
                                            </FieldLabel>
                                            <Controller
                                                control={permissionForm.control}
                                                name="group"
                                                render={({ field }) => (
                                                    <Select
                                                        value={field.value}
                                                        onValueChange={field.onChange}
                                                        disabled={
                                                            isLoadingOptions ||
                                                            isUpdatingPermission ||
                                                            !permissionSystemEnabled ||
                                                            !canManagePermissionGroups ||
                                                            permissionGroups.length === 0
                                                        }
                                                    >
                                                        <SelectTrigger
                                                            id="player-permission-group"
                                                            className="w-full"
                                                            aria-invalid={
                                                                permissionForm.formState.errors.group
                                                                    ? "true"
                                                                    : "false"
                                                            }
                                                        >
                                                            <SelectValue placeholder="Select a permission group" />
                                                        </SelectTrigger>
                                                        <SelectContent>
                                                            {permissionGroups.map((group) => (
                                                                <SelectItem key={group.id} value={group.id}>
                                                                    {group.name}
                                                                </SelectItem>
                                                            ))}
                                                        </SelectContent>
                                                    </Select>
                                                )}
                                            />
                                            <FieldDescription>
                                                Only admin and service accounts can assign
                                                permission groups.
                                            </FieldDescription>
                                            <FieldError errors={[permissionForm.formState.errors.group]} />
                                        </Field>

                                        <Field>
                                            <FieldLabel htmlFor="player-permission-duration">
                                                Duration
                                            </FieldLabel>
                                            <Input
                                                id="player-permission-duration"
                                                aria-invalid={
                                                    permissionForm.formState.errors.duration
                                                        ? "true"
                                                        : "false"
                                                }
                                                disabled={
                                                    isLoadingOptions ||
                                                    isUpdatingPermission ||
                                                    !permissionSystemEnabled ||
                                                    !canManagePermissionGroups
                                                }
                                                placeholder="-1 or 30d"
                                                {...permissionForm.register("duration")}
                                            />
                                            <FieldDescription>
                                                Use `-1` for permanent or values like `30d` and `1h
                                                30m`.
                                            </FieldDescription>
                                            <FieldError errors={[permissionForm.formState.errors.duration]} />
                                        </Field>
                                    </FieldGroup>

                                    <div className="flex flex-wrap items-center gap-3">
                                        <Button
                                            type="submit"
                                            disabled={
                                                isLoadingOptions ||
                                                isUpdatingPermission ||
                                                !permissionSystemEnabled ||
                                                !canManagePermissionGroups ||
                                                permissionGroups.length === 0
                                            }
                                        >
                                            {isUpdatingPermission ? (
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                            ) : null}
                                            Apply group
                                        </Button>
                                        <span className="text-xs text-muted-foreground">
                                            {!permissionSystemEnabled
                                                ? "Permission system is disabled in network settings."
                                                : !canManagePermissionGroups
                                                  ? "Your account cannot change permission groups."
                                                  : permissionGroups.length === 0
                                                    ? "No permission groups are available."
                                                    : "Changes apply immediately to the persisted player record."}
                                        </span>
                                    </div>
                                </form>
                            </CardContent>
                        </Card>

                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader>
                                <CardTitle className="flex items-center gap-2 text-base">
                                    <ShuffleIcon className="size-4 text-primary" />
                                    Move to server
                                </CardTitle>
                                <CardDescription>
                                    Transfer this player or your linked Minecraft account to the
                                    same live server.
                                </CardDescription>
                            </CardHeader>
                            <CardContent>
                                <form className="space-y-4" onSubmit={(event) => void handleTransferSubmit(event)}>
                                    <Field>
                                        <FieldLabel htmlFor="player-transfer-target">
                                            Target server
                                        </FieldLabel>
                                        <Controller
                                            control={transferForm.control}
                                            name="target"
                                            render={({ field }) => (
                                                <Select
                                                    value={field.value}
                                                    onValueChange={field.onChange}
                                                    disabled={
                                                        isLoadingOptions ||
                                                        isTransferringPlayer ||
                                                        isTransferringLinkedAccount ||
                                                        !activePlayer.online ||
                                                        availableTransferTargets.length === 0
                                                    }
                                                >
                                                    <SelectTrigger
                                                        id="player-transfer-target"
                                                        className="w-full"
                                                        aria-invalid={
                                                            transferForm.formState.errors.target
                                                                ? "true"
                                                                : "false"
                                                        }
                                                    >
                                                        <SelectValue placeholder="Select a running server" />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        {availableTransferTargets.map((server) => (
                                                            <SelectItem key={server.id} value={server.id}>
                                                                {server.displayName || server.podName || server.id}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            )}
                                        />
                                        <FieldDescription>
                                            Only running non-proxy targets are shown.
                                        </FieldDescription>
                                        <FieldError errors={[transferForm.formState.errors.target]} />
                                    </Field>

                                    <div className="flex flex-wrap items-center gap-3">
                                        <Button
                                            type="submit"
                                            variant="outline"
                                            disabled={
                                                isLoadingOptions ||
                                                isTransferringPlayer ||
                                                isTransferringLinkedAccount ||
                                                !activePlayer.online ||
                                                availableTransferTargets.length === 0
                                            }
                                        >
                                            {isTransferringPlayer ? (
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                            ) : null}
                                            Transfer player
                                        </Button>
                                        <Button
                                            type="button"
                                            disabled={
                                                isLoadingOptions ||
                                                isTransferringPlayer ||
                                                isTransferringLinkedAccount ||
                                                !activePlayer.online ||
                                                !activePlayer.serverId ||
                                                !linkedPlayerUuid
                                            }
                                            onClick={() => void handleTransferLinkedAccount()}
                                        >
                                            {isTransferringLinkedAccount ? (
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                            ) : null}
                                            Transfer linked account
                                        </Button>
                                    </div>

                                    <div className="text-xs text-muted-foreground">
                                        {!activePlayer.online
                                            ? "This player is offline and cannot be transferred."
                                            : availableTransferTargets.length === 0
                                              ? "No alternate running game servers are available right now."
                                              : !linkedPlayerUuid
                                                ? "Link your web account to Minecraft to use linked-account transfer."
                                                : "The linked-account transfer moves your own account to the selected player's current server."}
                                    </div>
                                </form>
                            </CardContent>
                        </Card>
                    </div>
                </div>
            </DialogContent>
        </Dialog>
    );
};

export default PlayerManagementDialog;
