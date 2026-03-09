import { useCallback, useEffect, useMemo, useState } from "react";
import { motion } from "motion/react";
import { createPortal } from "react-dom";
import { FiClock, FiShield, FiShuffle, FiTag, FiUsers, FiX } from "react-icons/fi";

import AppSelect from "@/components/AppSelect";
import AppToasts from "@/components/AppToasts";
import DetailStatCard from "@/components/DetailStatCard";
import FieldHintLabel from "@/components/FieldHintLabel";
import {
    listAllPermissionGroups,
    listAllServers,
    transferPlayerToTarget,
    updatePlayerPermissionGroup,
} from "@/lib/api";
import { hasAdminAccess } from "@/lib/roles";
import { formatDateTime } from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import type { PermissionGroupRecord } from "@/types/permission";
import type { PersistedPlayerRecord } from "@/types/player";
import type { ServerRecord } from "@/types/server";

interface PlayerManagementModalProps {
    player: PersistedPlayerRecord | null;
    onClose: () => void;
    onPlayerUpdated?: (player: PersistedPlayerRecord) => void;
    onTransferComplete?: () => void | Promise<void>;
}

const formatPermissionExpiry = (endMillis: number) => {
    if (endMillis === -1) {
        return "Permanent";
    }

    if (!Number.isFinite(endMillis) || endMillis <= 0) {
        return "--";
    }

    return formatDateTime(String(endMillis));
};

const PlayerManagementModal = ({
    player,
    onClose,
    onPlayerUpdated,
    onTransferComplete,
}: PlayerManagementModalProps) => {
    const session = useAuthStore((state) => state.session);
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const canManagePermissionGroups = hasAdminAccess(session?.user.role);

    const [activePlayer, setActivePlayer] = useState<PersistedPlayerRecord | null>(player);
    const [permissionGroups, setPermissionGroups] = useState<PermissionGroupRecord[]>([]);
    const [transferTargets, setTransferTargets] = useState<ServerRecord[]>([]);
    const [hasLoadedManageOptions, setHasLoadedManageOptions] = useState(false);
    const [isManageDataLoading, setIsManageDataLoading] = useState(false);
    const [permissionGroupDraft, setPermissionGroupDraft] = useState("");
    const [permissionDurationDraft, setPermissionDurationDraft] = useState("-1");
    const [transferTargetDraft, setTransferTargetDraft] = useState("");
    const [isUpdatingGroup, setIsUpdatingGroup] = useState(false);
    const [isTransferring, setIsTransferring] = useState(false);
    const [isTransferringMe, setIsTransferringMe] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadManageOptions = useCallback(async () => {
        if (hasLoadedManageOptions) {
            return;
        }

        setIsManageDataLoading(true);

        try {
            const accessToken = await getValidAccessToken();
            const [nextPermissionGroups, nextServers] = await Promise.all([
                canManagePermissionGroups && permissionSystemEnabled
                    ? listAllPermissionGroups(accessToken)
                    : Promise.resolve<PermissionGroupRecord[]>([]),
                listAllServers(accessToken),
            ]);

            setPermissionGroups(
                [...nextPermissionGroups].sort(
                    (left, right) =>
                        left.weight - right.weight || left.name.localeCompare(right.name),
                ),
            );
            setTransferTargets(
                nextServers
                    .filter(
                        (server) =>
                            server.state.toUpperCase() === "RUNNING" &&
                            server.type.toUpperCase() !== "PROXY",
                    )
                    .sort(
                        (left, right) =>
                            left.podName.localeCompare(right.podName) ||
                            left.displayName.localeCompare(right.displayName) ||
                            left.id.localeCompare(right.id),
                    ),
            );
            setHasLoadedManageOptions(true);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load player actions.",
            );
        } finally {
            setIsManageDataLoading(false);
        }
    }, [
        canManagePermissionGroups,
        getValidAccessToken,
        hasLoadedManageOptions,
        permissionSystemEnabled,
    ]);

    useEffect(() => {
        setActivePlayer(player);
        setHasLoadedManageOptions(false);
        setPermissionGroups([]);
        setTransferTargets([]);
        setPermissionGroupDraft(player?.permission.group ?? "");
        setPermissionDurationDraft("-1");
        setTransferTargetDraft("");
        setErrorMessage(null);
        setSuccessMessage(null);
    }, [player]);

    useEffect(() => {
        if (!player || hasLoadedManageOptions) {
            return;
        }

        void loadManageOptions();
    }, [hasLoadedManageOptions, loadManageOptions, player]);

    useEffect(() => {
        if (!successMessage) {
            return;
        }

        const timeoutId = window.setTimeout(() => {
            setSuccessMessage(null);
        }, 3000);

        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [successMessage]);

    const closeManageModal = (force = false) => {
        if (!force && (isUpdatingGroup || isTransferring || isTransferringMe)) {
            return;
        }

        onClose();
    };

    const applyUpdatedPlayer = (updatedPlayer: PersistedPlayerRecord) => {
        setActivePlayer((currentValue) =>
            currentValue?.uuid === updatedPlayer.uuid
                ? {
                      ...currentValue,
                      permission: updatedPlayer.permission,
                      online: updatedPlayer.online,
                      proxyId: updatedPlayer.proxyId,
                      serverId: updatedPlayer.serverId,
                      connectedAt: updatedPlayer.connectedAt,
                  }
                : currentValue,
        );
        onPlayerUpdated?.(updatedPlayer);
    };

    const handleApplyPermissionGroup = async () => {
        if (!activePlayer) {
            return;
        }

        if (!permissionSystemEnabled) {
            setErrorMessage("Permission system is disabled in network settings.");
            return;
        }

        if (!permissionGroupDraft.trim()) {
            setErrorMessage("Choose a permission group first.");
            return;
        }

        if (!permissionDurationDraft.trim()) {
            setErrorMessage("Enter a duration such as -1, 30d, or 1h 30m.");
            return;
        }

        setIsUpdatingGroup(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const updatedPlayer = await updatePlayerPermissionGroup(
                accessToken,
                activePlayer.uuid,
                permissionGroupDraft.trim(),
                permissionDurationDraft.trim(),
            );

            applyUpdatedPlayer(updatedPlayer);
            setSuccessMessage(
                `Updated ${activePlayer.name} to permission group ${permissionGroupDraft.trim()}.`,
            );
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Unable to update this player's permission group.",
            );
        } finally {
            setIsUpdatingGroup(false);
        }
    };

    const handleTransferPlayer = async () => {
        if (!activePlayer) {
            return;
        }

        if (!activePlayer.online) {
            setErrorMessage("Only online players can be moved to another server.");
            return;
        }

        if (!transferTargetDraft.trim()) {
            setErrorMessage("Choose a target server first.");
            return;
        }

        setIsTransferring(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();

            await transferPlayerToTarget(
                accessToken,
                activePlayer.uuid,
                transferTargetDraft.trim(),
            );

            await onTransferComplete?.();
            closeManageModal(true);
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "Unable to move this player.");
        } finally {
            setIsTransferring(false);
        }
    };

    const handleTransferMe = async () => {
        if (!activePlayer) {
            return;
        }

        const linkedPlayerUuid = session?.user.linkedPlayerUuid?.trim() ?? "";

        if (!linkedPlayerUuid) {
            setErrorMessage(
                "Your web account is not linked to Minecraft. Use the global link modal first.",
            );
            return;
        }

        if (!activePlayer.online || !activePlayer.serverId) {
            setErrorMessage("This player is not connected to a target game server right now.");
            return;
        }

        if (linkedPlayerUuid === activePlayer.uuid) {
            setErrorMessage("You are already this player.");
            return;
        }

        setIsTransferringMe(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();

            await transferPlayerToTarget(accessToken, linkedPlayerUuid, activePlayer.serverId);

            await onTransferComplete?.();
            setSuccessMessage(`Transfer requested: you -> ${activePlayer.serverId}.`);
            closeManageModal(true);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to transfer your account.",
            );
        } finally {
            setIsTransferringMe(false);
        }
    };

    const availableTransferTargets = useMemo(
        () =>
            transferTargets.filter(
                (server) =>
                    !activePlayer || !activePlayer.serverId || server.id !== activePlayer.serverId,
            ),
        [activePlayer, transferTargets],
    );
    const hasPermissionGroupOption = permissionGroups.some(
        (group) => group.id === permissionGroupDraft,
    );

    if (!activePlayer) {
        return null;
    }

    return createPortal(
        <>
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                              {
                                  id: "player-modal-error",
                                  message: errorMessage,
                                  onDismiss: () => setErrorMessage(null),
                                  tone: "error" as const,
                              },
                          ]
                        : []),
                    ...(successMessage
                        ? [
                              {
                                  id: "player-modal-success",
                                  message: successMessage,
                                  onDismiss: () => setSuccessMessage(null),
                                  tone: "success" as const,
                              },
                          ]
                        : []),
                ]}
            />

            <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                <motion.div
                    initial={{ y: 12, opacity: 0 }}
                    animate={{ y: 0, opacity: 1 }}
                    transition={{ duration: 0.25, ease: "easeOut" }}
                    className="flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                >
                    <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                        <div>
                            <h3 className="text-base font-semibold text-white">Manage Player</h3>
                            <p className="text-sm text-slate-400">
                                {activePlayer.name}
                                <span className="ml-2 font-mono text-xs text-slate-500">
                                    {activePlayer.uuid}
                                </span>
                            </p>
                        </div>
                        <button
                            type="button"
                            onClick={() => closeManageModal()}
                            className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                            aria-label="Close"
                        >
                            <FiX className="h-4 w-4" />
                        </button>
                    </div>

                    <div className="flex-1 space-y-6 overflow-y-auto p-6">
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
                            <DetailStatCard
                                label="Current Group"
                                value={activePlayer.permission.group}
                                meta={formatPermissionExpiry(activePlayer.permission.endMillis)}
                                icon={FiTag}
                                tone="primary"
                                compact
                            />
                            <DetailStatCard
                                label="Status"
                                value={activePlayer.online ? "Online" : "Offline"}
                                meta={
                                    activePlayer.online
                                        ? "Eligible for live actions"
                                        : "Cannot be moved now"
                                }
                                icon={FiUsers}
                                tone={activePlayer.online ? "success" : "neutral"}
                                compact
                            />
                            <DetailStatCard
                                label="Connected"
                                value={
                                    <span className="text-sm font-semibold whitespace-nowrap text-slate-100">
                                        {formatDateTime(activePlayer.connectedAt)}
                                    </span>
                                }
                                meta="Current network session"
                                icon={FiClock}
                                tone={activePlayer.online ? "success" : "neutral"}
                                compact
                            />
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <div className="space-y-4">
                                <div>
                                    <h4 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-400">
                                        <FiShield className="h-4 w-4 text-primary" />
                                        Permission Group
                                    </h4>
                                    <p className="mt-1 text-sm text-slate-500">
                                        Apply a permanent or temporary permission group to this
                                        player.
                                    </p>
                                </div>
                                <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_11rem_auto] lg:items-end">
                                    <AppSelect
                                        label="Permission Group"
                                        labelHint="Select the group that should be assigned to this player."
                                        value={
                                            hasPermissionGroupOption
                                                ? permissionGroupDraft
                                                : "__current__"
                                        }
                                        onChangeValue={setPermissionGroupDraft}
                                        disabled={
                                            !permissionSystemEnabled ||
                                            !canManagePermissionGroups ||
                                            isManageDataLoading ||
                                            permissionGroups.length === 0
                                        }
                                    >
                                        {!hasPermissionGroupOption && permissionGroupDraft ? (
                                            <option value="__current__" disabled>
                                                {permissionGroupDraft}
                                            </option>
                                        ) : null}
                                        {permissionGroups.map((group) => (
                                            <option key={group.id} value={group.id}>
                                                {group.name}
                                            </option>
                                        ))}
                                    </AppSelect>
                                    <div className="app-field-stack">
                                        <FieldHintLabel
                                            label="Duration"
                                            hint="Use -1 for permanent or values like 30d, 12h, or 1h 30m."
                                        />
                                        <input
                                            type="text"
                                            value={permissionDurationDraft}
                                            onChange={(event) =>
                                                setPermissionDurationDraft(event.target.value)
                                            }
                                            disabled={
                                                !permissionSystemEnabled ||
                                                !canManagePermissionGroups ||
                                                isManageDataLoading ||
                                                isUpdatingGroup
                                            }
                                            className="app-input-field rounded-lg border border-slate-700 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10"
                                            placeholder="-1 or 30d"
                                        />
                                    </div>
                                    <button
                                        type="button"
                                        disabled={
                                            isManageDataLoading ||
                                            isUpdatingGroup ||
                                            !permissionSystemEnabled ||
                                            !canManagePermissionGroups ||
                                            permissionGroups.length === 0 ||
                                            !permissionGroupDraft ||
                                            !permissionDurationDraft.trim()
                                        }
                                        onClick={() => void handleApplyPermissionGroup()}
                                        className="app-button-field button-hover-lift button-shadow-primary inline-flex min-w-36 items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold whitespace-nowrap text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                                    >
                                        {isUpdatingGroup ? "Saving..." : "Apply Group"}
                                    </button>
                                </div>
                                <p className="text-xs text-slate-500">
                                    {isManageDataLoading
                                        ? "Loading available permission groups..."
                                        : !permissionSystemEnabled
                                          ? "Permission system is disabled in network settings."
                                          : !canManagePermissionGroups
                                            ? "Only admin and service accounts can change permission groups."
                                            : permissionGroups.length === 0
                                              ? "No permission groups are available."
                                              : "Use -1 for permanent, or values like 30d or 1h 30m."}
                                </p>
                            </div>
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <div className="space-y-4">
                                <div>
                                    <h4 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-400">
                                        <FiShuffle className="h-4 w-4 text-primary" />
                                        Move To Server
                                    </h4>
                                    <p className="mt-1 text-sm text-slate-500">
                                        Transfer this player to another running server instance.
                                    </p>
                                </div>
                                <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_auto_auto] lg:items-end">
                                    <AppSelect
                                        label="Target Server"
                                        labelHint="Choose the running game server where this player should be moved."
                                        value={transferTargetDraft}
                                        onChangeValue={setTransferTargetDraft}
                                        disabled={
                                            isManageDataLoading ||
                                            !activePlayer.online ||
                                            availableTransferTargets.length === 0
                                        }
                                    >
                                        <option value="">Choose target server</option>
                                        {availableTransferTargets.map((server) => (
                                            <option key={server.id} value={server.id}>
                                                {server.podName || server.displayName || server.id}
                                            </option>
                                        ))}
                                    </AppSelect>
                                    <button
                                        type="button"
                                        disabled={
                                            isManageDataLoading ||
                                            isTransferring ||
                                            isTransferringMe ||
                                            !activePlayer.online ||
                                            !transferTargetDraft
                                        }
                                        onClick={() => void handleTransferPlayer()}
                                        className="app-button-field button-hover-lift button-shadow-neutral inline-flex min-w-36 items-center justify-center gap-2 rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold whitespace-nowrap text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                                    >
                                        {isTransferring ? "Moving..." : "Transfer Player"}
                                    </button>
                                    <button
                                        type="button"
                                        disabled={
                                            isManageDataLoading ||
                                            isTransferring ||
                                            isTransferringMe ||
                                            !activePlayer.online ||
                                            !activePlayer.serverId
                                        }
                                        onClick={() => void handleTransferMe()}
                                        className="app-button-field button-hover-lift button-shadow-primary inline-flex min-w-36 items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold whitespace-nowrap text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                                    >
                                        {isTransferringMe ? "Transferring..." : "Transfer Me"}
                                    </button>
                                </div>
                                <p className="text-xs text-slate-500">
                                    {!activePlayer.online
                                        ? "This player is currently offline and cannot be transferred."
                                        : isManageDataLoading
                                          ? "Loading running server targets..."
                                          : availableTransferTargets.length === 0
                                            ? "No alternate running game servers are available right now."
                                            : !session?.user.linkedPlayerUuid
                                              ? "Link your account through the global link modal before using Transfer Me."
                                              : "Only currently running static and dynamic servers are shown."}
                                </p>
                            </div>
                        </div>
                    </div>
                </motion.div>
            </div>
        </>,
        document.body,
    );
};

export default PlayerManagementModal;
