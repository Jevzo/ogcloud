import { zodResolver } from "@hookform/resolvers/zod";
import {
    ArrowLeftIcon,
    CopyIcon,
    LoaderCircleIcon,
    LogOutIcon,
    ShieldAlertIcon,
    ShieldCheckIcon,
    ShuffleIcon,
    UsersIcon,
} from "lucide-react";
import { type ReactNode, useEffect, useMemo, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { Link, useParams } from "react-router";
import { toast } from "sonner";

import { transferPlayerToTarget, updatePlayerPermissionGroup } from "@/api";
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
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { PageReveal } from "@/components/ui/page-reveal";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { hasAdminAccess } from "@/features/auth/lib/roles";
import { usePlayerDetailsQuery } from "@/features/players/hooks/use-player-details-query";
import { usePlayerManagementOptionsQuery } from "@/features/players/hooks/use-player-management-options-query";
import {
    getPermissionGrantLabel,
    getPlayerStatusBadgeClassName,
} from "@/features/players/lib/player-display";
import {
    playerPermissionAssignmentFormSchema,
    playerTransferFormSchema,
    type PlayerPermissionAssignmentFormValues,
    type PlayerTransferFormValues,
} from "@/features/players/schemas";
import { formatDateTime } from "@/features/servers/lib/server-display";
import { resolveMinecraftTextColor } from "@/lib/minecraft-text";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";

const alphaHex = (color: string, alpha: string) =>
    color.startsWith("#") && color.length === 7 ? `${color}${alpha}` : color;

const StatCard = ({ helper, label, value }: { helper: string; label: string; value: string }) => (
    <Card className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="pb-3">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                {label}
            </CardDescription>
            <CardTitle className="truncate text-xl tracking-tight">{value}</CardTitle>
        </CardHeader>
        <CardContent className="pt-0 text-sm text-muted-foreground">{helper}</CardContent>
    </Card>
);

const DetailRow = ({
    action,
    label,
    value,
    valueClassName,
}: {
    action?: ReactNode;
    label: string;
    value: ReactNode;
    valueClassName?: string;
}) => (
    <div className="flex flex-col gap-1 rounded-xl border border-border/70 bg-background/45 px-4 py-3">
        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">{label}</div>
        <div className="flex min-w-0 items-center gap-2">
            <div
                className={cn("min-w-0 flex-1 text-sm font-medium text-foreground", valueClassName)}
            >
                {value}
            </div>
            {action ? <div className="ml-auto shrink-0">{action}</div> : null}
        </div>
    </div>
);

const PlaceholderAction = ({ icon: Icon, label }: { icon: typeof LogOutIcon; label: string }) => (
    <Tooltip>
        <TooltipTrigger asChild>
            <span className="inline-flex">
                <Button variant="outline" disabled>
                    <Icon className="size-4" />
                    {label}
                </Button>
            </span>
        </TooltipTrigger>
        <TooltipContent side="bottom" sideOffset={8}>
            {label} is currently in the making.
        </TooltipContent>
    </Tooltip>
);

const PlayerDetailsPage = () => {
    const playerUuid = decodeURIComponent(useParams().playerUuid ?? "");
    const getAccessToken = useAccessToken();
    const session = useAuthStore((state) => state.session);
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const canManagePermissionGroups = hasAdminAccess(session?.user.role);
    const linkedPlayerUuid = session?.user.linkedPlayerUuid?.trim() ?? "";
    const { errorMessage, isLoading, lastUpdatedAt, player, refresh } =
        usePlayerDetailsQuery(playerUuid);
    const {
        errorMessage: operationsErrorMessage,
        isLoading: isLoadingOptions,
        permissionGroups,
        transferTargets,
    } = usePlayerManagementOptionsQuery({
        enabled: Boolean(player),
        permissionSystemEnabled,
    });
    const permissionForm = useForm<PlayerPermissionAssignmentFormValues>({
        resolver: zodResolver(playerPermissionAssignmentFormSchema),
        defaultValues: { duration: "-1", group: "" },
    });
    const transferForm = useForm<PlayerTransferFormValues>({
        resolver: zodResolver(playerTransferFormSchema),
        defaultValues: { target: "" },
    });
    const [isUpdatingPermission, setIsUpdatingPermission] = useState(false);
    const [isTransferringPlayer, setIsTransferringPlayer] = useState(false);
    const [isTransferringLinkedAccount, setIsTransferringLinkedAccount] = useState(false);

    useEffect(() => {
        permissionForm.reset({ duration: "-1", group: player?.permission.group ?? "" });
        transferForm.reset({ target: "" });
    }, [permissionForm, player, transferForm]);

    const currentPermissionGroup = useMemo(
        () => permissionGroups.find((group) => group.id === player?.permission.group),
        [permissionGroups, player?.permission.group],
    );
    const permissionGroupColor = resolveMinecraftTextColor(
        currentPermissionGroup?.display.nameColor,
    );
    const proxyId = player?.proxyId ?? null;
    const serverId = player?.serverId ?? null;
    const isSelfLinkedPlayer = linkedPlayerUuid !== "" && linkedPlayerUuid === player?.uuid;
    const availableTransferTargets = useMemo(
        () =>
            transferTargets.filter((server) => !player?.serverId || server.id !== player.serverId),
        [player?.serverId, transferTargets],
    );
    const hasFreshData = lastUpdatedAt !== null;

    const copyValue = async (value: string, label: string) => {
        try {
            await navigator.clipboard.writeText(value);
            toast.success(`${label} copied.`);
        } catch {
            toast.error(`Unable to copy ${label.toLowerCase()}.`);
        }
    };

    const handlePermissionSubmit = permissionForm.handleSubmit(async (values) => {
        if (!player) return;
        if (!permissionSystemEnabled) {
            toast.error("Permission system is disabled in network settings.");
            return;
        }
        setIsUpdatingPermission(true);
        try {
            const accessToken = await getAccessToken();
            const updatedPlayer = await updatePlayerPermissionGroup(
                accessToken,
                player.uuid,
                values.group.trim(),
                values.duration.trim(),
            );
            permissionForm.reset({ duration: "-1", group: updatedPlayer.permission.group });
            toast.success(`Updated ${updatedPlayer.name} to ${updatedPlayer.permission.group}.`);
            await refresh(false);
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
        if (!player) return;
        if (!player.online) {
            toast.error("Only online players can be moved to another server.");
            return;
        }
        setIsTransferringPlayer(true);
        try {
            const accessToken = await getAccessToken();
            await transferPlayerToTarget(accessToken, player.uuid, values.target.trim());
            toast.success(`Transfer requested for ${player.name}.`);
            transferForm.reset({ target: "" });
            await refresh(false);
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to move this player.");
        } finally {
            setIsTransferringPlayer(false);
        }
    });

    const handleTransferLinkedAccount = async () => {
        if (!player) return;
        if (!linkedPlayerUuid) {
            toast.error("Your web account is not linked to Minecraft.");
            return;
        }
        if (!player.online || !player.serverId) {
            toast.error("This player is not connected to a target game server right now.");
            return;
        }
        if (linkedPlayerUuid === player.uuid) {
            return;
        }
        setIsTransferringLinkedAccount(true);
        try {
            const accessToken = await getAccessToken();
            await transferPlayerToTarget(accessToken, linkedPlayerUuid, player.serverId);
            toast.success(`Transfer requested: you -> ${player.serverId}.`);
            await refresh(false);
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to transfer your linked account.",
            );
        } finally {
            setIsTransferringLinkedAccount(false);
        }
    };

    if (isLoading && !player) return null;

    if (errorMessage && !player) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Player Error
                    </CardDescription>
                    <CardTitle className="text-destructive">Unable to load this player</CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
                <CardFooter>
                    <Button variant="outline" asChild>
                        <Link to="/players">
                            <ArrowLeftIcon className="size-4" />
                            Back to players
                        </Link>
                    </Button>
                </CardFooter>
            </Card>
        );
    }

    return (
        <TooltipProvider delayDuration={150}>
            <PageReveal className="space-y-4">
                <div className="space-y-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                        <Button variant="ghost" size="sm" asChild className="-ml-2 w-fit">
                            <Link to="/players">
                                <ArrowLeftIcon className="size-4" />
                                Back to players
                            </Link>
                        </Button>
                        <div className="flex flex-wrap items-center gap-2 xl:justify-end">
                            <PlaceholderAction icon={LogOutIcon} label="Kick player" />
                            <PlaceholderAction icon={ShieldAlertIcon} label="Ban player" />
                        </div>
                    </div>
                    <div className="space-y-3">
                        <div className="flex flex-wrap items-center gap-2">
                            {player ? (
                                <Badge
                                    variant="outline"
                                    className={getPlayerStatusBadgeClassName(player.online)}
                                >
                                    {player.online ? "Online" : "Offline"}
                                </Badge>
                            ) : null}
                            {player ? (
                                <Badge
                                    variant="outline"
                                    className="border-transparent"
                                    style={{
                                        backgroundColor: alphaHex(permissionGroupColor, "14"),
                                        borderColor: alphaHex(permissionGroupColor, "40"),
                                        color: permissionGroupColor,
                                    }}
                                >
                                    {currentPermissionGroup?.name ?? player.permission.group}
                                </Badge>
                            ) : null}
                        </div>
                        <div>
                            <CardTitle className="flex items-center gap-2 text-2xl tracking-tight">
                                <UsersIcon className="size-5 text-primary" />
                                {player?.name ?? playerUuid}
                            </CardTitle>
                            <CardDescription className="mt-2 max-w-3xl text-sm leading-6">
                                Persisted identity, permission assignment, and live routing state
                                for the selected player record.
                            </CardDescription>
                        </div>
                    </div>
                </div>

                {errorMessage && hasFreshData ? (
                    <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                        <CardHeader className="pb-3">
                            <CardTitle className="text-base text-amber-200">
                                Showing the latest successful player snapshot
                            </CardTitle>
                            <CardDescription className="text-sm text-amber-100/80">
                                {errorMessage}
                            </CardDescription>
                        </CardHeader>
                    </Card>
                ) : null}

                {operationsErrorMessage ? (
                    <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                        <CardHeader className="pb-3">
                            <CardTitle className="text-base text-amber-200">
                                Player operations are partially unavailable
                            </CardTitle>
                            <CardDescription className="text-sm text-amber-100/80">
                                {operationsErrorMessage}
                            </CardDescription>
                        </CardHeader>
                    </Card>
                ) : null}

                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <StatCard
                        label="Status"
                        value={player?.online ? "Online" : "Offline"}
                        helper="Whether this player currently has an active routed session."
                    />
                    <StatCard
                        label="Permission"
                        value={currentPermissionGroup?.name ?? player?.permission.group ?? "--"}
                        helper="Permission group currently persisted for this player."
                    />
                    <StatCard
                        label="Proxy"
                        value={player?.proxyDisplayName ?? player?.proxyId ?? "--"}
                        helper="Current proxy edge handling the live player session."
                    />
                    <StatCard
                        label="Server"
                        value={player?.serverDisplayName ?? player?.serverId ?? "--"}
                        helper="Current backend server target if the player is online."
                    />
                </div>

                <Tabs defaultValue="overview" className="gap-4">
                    <TabsList variant="line">
                        <TabsTrigger value="overview">Overview</TabsTrigger>
                        <TabsTrigger value="permissions">Permission handling</TabsTrigger>
                        <TabsTrigger value="transfer">Move server</TabsTrigger>
                    </TabsList>

                    <TabsContent value="overview" className="space-y-4">
                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader className="pb-4">
                                <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                    Identity
                                </CardDescription>
                                <CardTitle className="text-base">Identity and timeline</CardTitle>
                            </CardHeader>
                            <CardContent className="grid gap-3 md:grid-cols-2">
                                <DetailRow label="Display name" value={player?.name ?? "--"} />
                                <DetailRow
                                    label="UUID"
                                    value={player?.uuid ?? playerUuid}
                                    valueClassName="truncate whitespace-nowrap"
                                    action={
                                        player?.uuid ? (
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="icon-xs"
                                                className="shrink-0"
                                                onClick={() => void copyValue(player.uuid, "UUID")}
                                                aria-label="Copy UUID"
                                                title="Copy UUID"
                                            >
                                                <CopyIcon className="size-4" />
                                            </Button>
                                        ) : null
                                    }
                                />
                                <DetailRow
                                    label="First join"
                                    value={formatDateTime(player?.firstJoin)}
                                />
                                <DetailRow
                                    label="Connected since"
                                    value={formatDateTime(player?.connectedAt)}
                                />
                            </CardContent>
                        </Card>

                        <div className="grid gap-4 xl:grid-cols-2">
                            <Card className="border border-border/70 bg-card/85 shadow-none">
                                <CardHeader className="pb-4">
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Permissions
                                    </CardDescription>
                                    <CardTitle className="text-base">
                                        Permission assignment
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="grid gap-3 md:grid-cols-2">
                                    <DetailRow
                                        label="Permission group"
                                        value={
                                            <Badge
                                                variant="outline"
                                                className="max-w-full truncate border-transparent"
                                                style={{
                                                    backgroundColor: alphaHex(
                                                        permissionGroupColor,
                                                        "14",
                                                    ),
                                                    borderColor: alphaHex(
                                                        permissionGroupColor,
                                                        "40",
                                                    ),
                                                    color: permissionGroupColor,
                                                }}
                                            >
                                                {currentPermissionGroup?.name ??
                                                    player?.permission.group ??
                                                    "--"}
                                            </Badge>
                                        }
                                    />
                                    <DetailRow
                                        label="Grant type"
                                        value={
                                            player
                                                ? getPermissionGrantLabel(
                                                      player.permission.endMillis,
                                                  )
                                                : "--"
                                        }
                                    />
                                </CardContent>
                            </Card>

                            <Card className="border border-border/70 bg-card/85 shadow-none">
                                <CardHeader className="pb-4">
                                    <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                        Routing
                                    </CardDescription>
                                    <CardTitle className="text-base">
                                        Current route envelope
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="grid gap-3 md:grid-cols-2">
                                    <DetailRow
                                        label="Proxy ID"
                                        value={proxyId ?? "--"}
                                        valueClassName="truncate whitespace-nowrap"
                                        action={
                                            proxyId ? (
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon-xs"
                                                    className="shrink-0"
                                                    onClick={() =>
                                                        void copyValue(proxyId, "Proxy ID")
                                                    }
                                                    aria-label="Copy proxy ID"
                                                    title="Copy proxy ID"
                                                >
                                                    <CopyIcon className="size-4" />
                                                </Button>
                                            ) : null
                                        }
                                    />
                                    <DetailRow
                                        label="Server ID"
                                        value={serverId ?? "--"}
                                        valueClassName="truncate whitespace-nowrap"
                                        action={
                                            serverId ? (
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon-xs"
                                                    className="shrink-0"
                                                    onClick={() =>
                                                        void copyValue(serverId, "Server ID")
                                                    }
                                                    aria-label="Copy server ID"
                                                    title="Copy server ID"
                                                >
                                                    <CopyIcon className="size-4" />
                                                </Button>
                                            ) : null
                                        }
                                    />
                                </CardContent>
                            </Card>
                        </div>
                    </TabsContent>

                    <TabsContent value="permissions">
                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader className="border-b border-border/70 pb-4">
                                <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                    Player operations
                                </CardDescription>
                                <CardTitle className="flex items-center gap-2 text-base">
                                    <ShieldCheckIcon className="size-4 text-primary" />
                                    Permission handling
                                </CardTitle>
                                <CardDescription>
                                    Apply a permanent or temporary permission group to this player.
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-4 pt-4">
                                <form
                                    className="space-y-4"
                                    onSubmit={(event) => void handlePermissionSubmit(event)}
                                >
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
                                                                permissionForm.formState.errors
                                                                    .group
                                                                    ? "true"
                                                                    : "false"
                                                            }
                                                        >
                                                            <SelectValue placeholder="Select a permission group" />
                                                        </SelectTrigger>
                                                        <SelectContent>
                                                            {permissionGroups.map((group) => (
                                                                <SelectItem
                                                                    key={group.id}
                                                                    value={group.id}
                                                                >
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
                                            <FieldError
                                                errors={[permissionForm.formState.errors.group]}
                                            />
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
                                            <FieldError
                                                errors={[permissionForm.formState.errors.duration]}
                                            />
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
                    </TabsContent>

                    <TabsContent value="transfer">
                        <Card className="border border-border/70 bg-card/85 shadow-none">
                            <CardHeader className="border-b border-border/70 pb-4">
                                <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                    Player operations
                                </CardDescription>
                                <CardTitle className="flex items-center gap-2 text-base">
                                    <ShuffleIcon className="size-4 text-primary" />
                                    Move to server
                                </CardTitle>
                                <CardDescription>
                                    Transfer this player or your linked Minecraft account to the
                                    same live server.
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-4 pt-4">
                                <form
                                    className="space-y-4"
                                    onSubmit={(event) => void handleTransferSubmit(event)}
                                >
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
                                                        !player?.online ||
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
                                                            <SelectItem
                                                                key={server.id}
                                                                value={server.id}
                                                            >
                                                                {server.displayName ||
                                                                    server.podName ||
                                                                    server.id}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            )}
                                        />
                                        <FieldDescription>
                                            Only running non-proxy targets are shown.
                                        </FieldDescription>
                                        <FieldError
                                            errors={[transferForm.formState.errors.target]}
                                        />
                                    </Field>
                                    <div className="flex flex-wrap items-center gap-3">
                                        <Tooltip>
                                            <TooltipTrigger asChild>
                                                <span className="inline-flex">
                                                    <Button
                                                        type="submit"
                                                        variant="outline"
                                                        disabled={
                                                            isLoadingOptions ||
                                                            isTransferringPlayer ||
                                                            isTransferringLinkedAccount ||
                                                            !player?.online ||
                                                            availableTransferTargets.length === 0
                                                        }
                                                    >
                                                        {isTransferringPlayer ? (
                                                            <LoaderCircleIcon className="size-4 animate-spin" />
                                                        ) : null}
                                                        Transfer player
                                                    </Button>
                                                </span>
                                            </TooltipTrigger>
                                            {player?.online &&
                                            availableTransferTargets.length === 0 ? (
                                                <TooltipContent side="top" sideOffset={8}>
                                                    No alternative server is currently running.
                                                </TooltipContent>
                                            ) : null}
                                        </Tooltip>
                                        <Tooltip>
                                            <TooltipTrigger asChild>
                                                <span className="inline-flex">
                                                    <Button
                                                        type="button"
                                                        disabled={
                                                            isLoadingOptions ||
                                                            isTransferringPlayer ||
                                                            isTransferringLinkedAccount ||
                                                            !player?.online ||
                                                            !player?.serverId ||
                                                            !linkedPlayerUuid ||
                                                            isSelfLinkedPlayer
                                                        }
                                                        onClick={() =>
                                                            void handleTransferLinkedAccount()
                                                        }
                                                    >
                                                        {isTransferringLinkedAccount ? (
                                                            <LoaderCircleIcon className="size-4 animate-spin" />
                                                        ) : null}
                                                        Transfer linked account
                                                    </Button>
                                                </span>
                                            </TooltipTrigger>
                                            {isSelfLinkedPlayer ? (
                                                <TooltipContent side="top" sideOffset={8}>
                                                    You are the player.
                                                </TooltipContent>
                                            ) : null}
                                        </Tooltip>
                                    </div>
                                </form>
                            </CardContent>
                        </Card>
                    </TabsContent>
                </Tabs>
            </PageReveal>
        </TooltipProvider>
    );
};

export default PlayerDetailsPage;
