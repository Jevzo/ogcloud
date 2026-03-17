import { useEffect, useState } from "react";
import { motion } from "motion/react";
import {
    FiAlertTriangle,
    FiClock,
    FiLock,
    FiRefreshCw,
    FiSave,
    FiShield,
    FiX,
} from "react-icons/fi";

import AppNumberInput from "@/components/AppNumberInput";
import AppSelect from "@/components/AppSelect";
import FieldHintLabel from "@/components/FieldHintLabel";
import { RUNTIME_REFRESH_OPTIONS } from "@/lib/group-runtime";
import { useNetworkPageContext } from "@/pages/network/context";
import {
    createRestartConfirmationCode,
    formatNetworkLockDuration,
    formatNetworkLockType,
    getNetworkLockSummary,
} from "@/pages/network/utils";

const NetworkServerSettingsPage = () => {
    const {
        groups,
        isAdmin,
        isLoading,
        isRestartingNetwork,
        isTogglingMaintenance,
        locks,
        refreshingScope,
        requestNetworkRestart,
        requestRuntimeRefresh,
        saveSettings,
        setMaintenance,
        settings,
        showErrorMessage,
    } = useNetworkPageContext();

    const [maxPlayers, setMaxPlayers] = useState("");
    const [defaultGroup, setDefaultGroup] = useState("");
    const [isSaving, setIsSaving] = useState(false);
    const [isMaintenanceModalOpen, setIsMaintenanceModalOpen] = useState(false);
    const [isRestartModalOpen, setIsRestartModalOpen] = useState(false);
    const [restartConfirmationCode, setRestartConfirmationCode] = useState("");
    const [restartConfirmationInput, setRestartConfirmationInput] = useState("");

    useEffect(() => {
        if (!settings) {
            return;
        }

        setMaxPlayers(String(settings.maxPlayers));
        setDefaultGroup(settings.defaultGroup);
    }, [settings?.defaultGroup, settings?.maxPlayers]);

    const eligibleDefaultGroups = groups.filter((group) => group.type.toUpperCase() !== "PROXY");
    const normalizedDefaultGroup =
        eligibleDefaultGroups.some((group) => group.id === defaultGroup) ? defaultGroup : "";
    const networkRestartLock =
        locks.find((lock) => lock.type.toUpperCase() === "NETWORK_RESTART") ?? null;
    const maintenanceDisableBlocked = Boolean(settings?.maintenance && networkRestartLock);
    const restartButtonDisabled =
        !isAdmin ||
        !settings ||
        !settings.maintenance ||
        isRestartingNetwork ||
        Boolean(networkRestartLock);
    const isRestartConfirmationValid =
        restartConfirmationCode.length === 6 &&
        restartConfirmationInput.trim() === restartConfirmationCode;

    const openRestartModal = () => {
        setRestartConfirmationCode(createRestartConfirmationCode());
        setRestartConfirmationInput("");
        setIsRestartModalOpen(true);
    };

    const closeRestartModal = () => {
        if (isRestartingNetwork) {
            return;
        }

        setIsRestartModalOpen(false);
        setRestartConfirmationCode("");
        setRestartConfirmationInput("");
    };

    const handleSave = async () => {
        if (!settings) {
            return;
        }

        const parsedMaxPlayers = Number.parseInt(maxPlayers, 10);

        if (!Number.isFinite(parsedMaxPlayers) || parsedMaxPlayers <= 0) {
            showErrorMessage("Max players must be a positive number.");
            return;
        }

        if (!normalizedDefaultGroup) {
            showErrorMessage("Choose a non-proxy default group.");
            return;
        }

        setIsSaving(true);

        try {
            const nextSettings = await saveSettings(
                {
                    maxPlayers: parsedMaxPlayers,
                    defaultGroup: normalizedDefaultGroup,
                },
                "Server settings saved.",
            );

            setMaxPlayers(String(nextSettings.maxPlayers));
            setDefaultGroup(nextSettings.defaultGroup);
        } finally {
            setIsSaving(false);
        }
    };

    const handleToggleMaintenance = async () => {
        if (!settings) {
            return;
        }

        if (!settings.maintenance) {
            setIsMaintenanceModalOpen(true);
            return;
        }

        if (maintenanceDisableBlocked) {
            return;
        }

        await setMaintenance(false);
    };

    const handleConfirmEnableMaintenance = async () => {
        await setMaintenance(true);
        setIsMaintenanceModalOpen(false);
    };

    const handleRequestNetworkRestart = async () => {
        if (!settings?.maintenance || restartConfirmationInput.trim() !== restartConfirmationCode) {
            return;
        }

        await requestNetworkRestart();
        closeRestartModal();
    };

    const restartButtonTitle = !isAdmin
        ? "Only admin and service accounts can restart the network."
        : !settings
          ? "Network settings are still loading."
          : !settings.maintenance
            ? "Enable network maintenance before requesting a restart."
            : networkRestartLock
              ? `A network restart lock is active for ${formatNetworkLockDuration(networkRestartLock.ttlSeconds)}.`
              : "Request a full network restart.";
    const maintenanceButtonTitle = !settings
        ? "Network settings are still loading."
        : maintenanceDisableBlocked && networkRestartLock
          ? `Wait ${formatNetworkLockDuration(networkRestartLock.ttlSeconds)} before disabling maintenance again.`
          : settings.maintenance
            ? "Disable network maintenance."
            : "Enable network maintenance.";

    return (
        <div className="space-y-6">
            <motion.section
                initial={{ y: 16, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
                className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]"
            >
                <div className="space-y-6">
                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Network Operations
                            </h3>
                        </div>
                        <div className="space-y-5 p-6">
                            {!settings ? (
                                <p className="text-sm text-slate-400">
                                    {isLoading
                                        ? "Loading server operations..."
                                        : "Network settings are unavailable."}
                                </p>
                            ) : (
                                <>
                                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Maintenance
                                            </p>
                                            <p
                                                className={`mt-1.5 text-sm font-semibold ${
                                                    settings.maintenance
                                                        ? "text-amber-300"
                                                        : "text-emerald-300"
                                                }`}
                                            >
                                                {settings.maintenance ? "Enabled" : "Disabled"}
                                            </p>
                                        </div>
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Restart Cooldown
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {networkRestartLock
                                                    ? formatNetworkLockDuration(
                                                          networkRestartLock.ttlSeconds,
                                                      )
                                                    : "Ready"}
                                            </p>
                                        </div>
                                    </div>

                                    {networkRestartLock ? (
                                        <div className="rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-4 text-sm text-amber-100">
                                            <div className="flex items-start gap-3">
                                                <FiClock className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
                                                <p>
                                                    A full network restart was already requested.
                                                    You can request another one in{" "}
                                                    {formatNetworkLockDuration(
                                                        networkRestartLock.ttlSeconds,
                                                    )}
                                                    .
                                                </p>
                                            </div>
                                        </div>
                                    ) : null}

                                    <div className="flex flex-wrap gap-3">
                                        <button
                                            type="button"
                                            title={maintenanceButtonTitle}
                                            onClick={() => void handleToggleMaintenance()}
                                            disabled={
                                                !settings ||
                                                isTogglingMaintenance ||
                                                maintenanceDisableBlocked
                                            }
                                            className={`app-button-field button-hover-lift inline-flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${
                                                settings.maintenance
                                                    ? "button-shadow-neutral border border-slate-700 text-slate-200"
                                                    : "button-shadow-warning bg-amber-500/12 text-amber-300"
                                            }`}
                                        >
                                            <FiShield className="h-4 w-4" />
                                            {isTogglingMaintenance
                                                ? "Updating..."
                                                : settings.maintenance
                                                  ? "Disable Maintenance"
                                                  : "Enable Maintenance"}
                                        </button>
                                        <button
                                            type="button"
                                            title={restartButtonTitle}
                                            onClick={openRestartModal}
                                            disabled={restartButtonDisabled}
                                            className="app-button-field button-hover-lift inline-flex items-center gap-2 rounded-lg border border-red-500/25 bg-red-500/10 px-4 py-2.5 text-sm font-semibold text-red-200 disabled:cursor-not-allowed disabled:opacity-60"
                                        >
                                            <FiRefreshCw
                                                className={`h-4 w-4 ${isRestartingNetwork ? "animate-spin" : ""}`}
                                            />
                                            {isRestartingNetwork ? "Requesting..." : "Restart Network"}
                                        </button>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>

                    {isAdmin ? (
                        <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                            <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                                <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                    Managed Runtime
                                </h3>
                            </div>
                            <div className="space-y-4 p-6">
                                <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-4 text-sm text-slate-300">
                                    Refresh managed runtime bundles after updating backend runtime
                                    assets or runtime configuration. This is a network-wide admin
                                    action.
                                </div>
                                <div className="space-y-3">
                                    {RUNTIME_REFRESH_OPTIONS.map((option) => {
                                        const isRefreshing = refreshingScope === option.scope;

                                        return (
                                            <div
                                                key={option.scope}
                                                className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-4"
                                            >
                                                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                                                    <div>
                                                        <h4 className="text-sm font-semibold text-white">
                                                            {option.label}
                                                        </h4>
                                                        <p className="mt-1 text-sm text-slate-400">
                                                            {option.description}
                                                        </p>
                                                    </div>
                                                    <button
                                                        type="button"
                                                        disabled={refreshingScope !== null}
                                                        onClick={() =>
                                                            void requestRuntimeRefresh(option.scope)
                                                        }
                                                        className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                                                    >
                                                        <FiRefreshCw
                                                            className={`h-4 w-4 ${isRefreshing ? "animate-spin" : ""}`}
                                                        />
                                                        {isRefreshing
                                                            ? "Requesting..."
                                                            : "Refresh Runtime"}
                                                    </button>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </div>
                    ) : null}
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm self-start">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Active Locks
                        </h3>
                    </div>
                    <div className="space-y-4 p-6">
                        {locks.length === 0 ? (
                            <div className="rounded-lg border border-dashed border-slate-700 bg-slate-950/35 px-4 py-6 text-sm text-slate-400">
                                {isLoading
                                    ? "Loading active locks..."
                                    : "No active synchronization locks."}
                            </div>
                        ) : (
                            locks.map((lock) => (
                                <div
                                    key={lock.key}
                                    className="rounded-xl border border-slate-800 bg-slate-950/35 px-4 py-4"
                                >
                                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <FiLock className="h-4 w-4 text-primary" />
                                                <p className="text-sm font-semibold text-white">
                                                    {formatNetworkLockType(lock.type)}
                                                </p>
                                            </div>
                                            <p className="mt-2 text-sm text-slate-400">
                                                {getNetworkLockSummary(lock)}
                                            </p>
                                        </div>
                                        <span className="inline-flex items-center gap-2 rounded-full border border-slate-700 px-3 py-1 text-xs font-semibold text-slate-300">
                                            <FiClock className="h-3.5 w-3.5" />
                                            {formatNetworkLockDuration(lock.ttlSeconds)}
                                        </span>
                                    </div>
                                    <div className="mt-4 grid grid-cols-1 gap-2 text-xs text-slate-500">
                                        {lock.targetId ? (
                                            <p>
                                                Target:{" "}
                                                <span className="font-medium text-slate-300">
                                                    {lock.targetId}
                                                </span>
                                            </p>
                                        ) : null}
                                        <p className="truncate">
                                            Key:{" "}
                                            <span className="font-mono text-slate-400">
                                                {lock.key}
                                            </span>
                                        </p>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </motion.section>

            <motion.section
                initial={{ y: 18, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
                className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
            >
                <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                    <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                        Server Capacity
                    </h3>
                </div>
                <div className="space-y-6 p-6">
                    {!settings ? (
                        <p className="text-sm text-slate-400">
                            {isLoading
                                ? "Loading server capacity settings..."
                                : "Server capacity settings are unavailable."}
                        </p>
                    ) : (
                        <>
                            <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Max Players"
                                        hint="Maximum player slots shown and enforced across the whole network."
                                    />
                                    <AppNumberInput
                                        value={maxPlayers}
                                        onChangeValue={setMaxPlayers}
                                        min={1}
                                        disabled={isLoading || !settings}
                                    />
                                </div>
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Default Group"
                                        hint="Fallback game group used when no explicit target is provided."
                                    />
                                    <AppSelect
                                        value={normalizedDefaultGroup}
                                        onChangeValue={setDefaultGroup}
                                        disabled={
                                            isLoading || !settings || eligibleDefaultGroups.length === 0
                                        }
                                    >
                                        <option value="">
                                            {eligibleDefaultGroups.length === 0
                                                ? "No eligible groups available"
                                                : "Choose default group"}
                                        </option>
                                        {eligibleDefaultGroups.map((group) => (
                                            <option key={group.id} value={group.id}>
                                                {group.id}
                                            </option>
                                        ))}
                                    </AppSelect>
                                </div>
                            </div>

                            <div className="flex justify-end border-t border-slate-800/70 pt-5">
                                <button
                                    type="button"
                                    onClick={() => void handleSave()}
                                    disabled={!settings || isSaving}
                                    className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                    <FiSave className="h-4 w-4" />
                                    {isSaving ? "Saving..." : "Save Changes"}
                                </button>
                            </div>
                        </>
                    )}
                </div>
            </motion.section>

            {isMaintenanceModalOpen ? (
                <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                    <motion.div
                        initial={{ y: 12, opacity: 0 }}
                        animate={{ y: 0, opacity: 1 }}
                        transition={{ duration: 0.25, ease: "easeOut" }}
                        className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                    >
                        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                            <div>
                                <h3 className="text-base font-semibold text-white">
                                    Enable Network Maintenance
                                </h3>
                                <p className="text-sm text-slate-400">
                                    This affects the full network immediately.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setIsMaintenanceModalOpen(false)}
                                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                                aria-label="Close"
                            >
                                <FiX className="h-4 w-4" />
                            </button>
                        </div>

                        <div className="space-y-4 px-6 py-5">
                            <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-4 text-sm text-red-100">
                                <div className="flex items-start gap-3">
                                    <FiAlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300" />
                                    <p>
                                        Enabling maintenance will remove all players from the
                                        network and block normal joins until you disable it again.
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
                            <button
                                type="button"
                                onClick={() => setIsMaintenanceModalOpen(false)}
                                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                disabled={isTogglingMaintenance}
                                onClick={() => void handleConfirmEnableMaintenance()}
                                className="app-button-field button-hover-lift button-shadow-warning rounded-lg bg-amber-500/12 px-4 py-2.5 text-sm font-semibold text-amber-300 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {isTogglingMaintenance ? "Enabling..." : "Enable Maintenance"}
                            </button>
                        </div>
                    </motion.div>
                </div>
            ) : null}

            {isRestartModalOpen ? (
                <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                    <motion.div
                        initial={{ y: 12, opacity: 0 }}
                        animate={{ y: 0, opacity: 1 }}
                        transition={{ duration: 0.25, ease: "easeOut" }}
                        className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                    >
                        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                            <div>
                                <h3 className="text-base font-semibold text-white">
                                    Restart Network
                                </h3>
                                <p className="text-sm text-slate-400">
                                    This requests a full phased network restart.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={closeRestartModal}
                                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                                aria-label="Close"
                            >
                                <FiX className="h-4 w-4" />
                            </button>
                        </div>

                        <div className="space-y-4 px-6 py-5">
                            <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-4 text-sm text-red-100">
                                <div className="flex items-start gap-3">
                                    <FiAlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300" />
                                    <p>
                                        The backend will restart proxies, the default group, and
                                        the remaining groups in phases. Type the code below to
                                        confirm this destructive action.
                                    </p>
                                </div>
                            </div>

                            <div className="rounded-lg border border-slate-700 bg-slate-950/60 px-4 py-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Confirmation Code
                                </p>
                                <p className="mt-2 font-mono text-2xl font-semibold tracking-[0.35em] text-white">
                                    {restartConfirmationCode}
                                </p>
                            </div>

                            <div className="app-field-stack">
                                <FieldHintLabel
                                    label="Type the 6-digit code"
                                    hint="This is a frontend confirmation step to reduce accidental restart requests."
                                />
                                <input
                                    type="text"
                                    inputMode="numeric"
                                    value={restartConfirmationInput}
                                    onChange={(event) =>
                                        setRestartConfirmationInput(
                                            event.target.value.replace(/\D/g, "").slice(0, 6),
                                        )
                                    }
                                    maxLength={6}
                                    autoFocus
                                    className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm font-mono tracking-[0.28em] text-slate-100 outline-none transition focus:border-red-400/50 focus:ring-2 focus:ring-red-400/10"
                                    placeholder="000000"
                                />
                            </div>
                        </div>

                        <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
                            <button
                                type="button"
                                onClick={closeRestartModal}
                                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                disabled={!isRestartConfirmationValid || isRestartingNetwork}
                                onClick={() => void handleRequestNetworkRestart()}
                                className="app-button-field button-hover-lift rounded-lg bg-red-500/12 px-4 py-2.5 text-sm font-semibold text-red-200 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {isRestartingNetwork ? "Requesting..." : "Request Restart"}
                            </button>
                        </div>
                    </motion.div>
                </div>
            ) : null}
        </div>
    );
};

export default NetworkServerSettingsPage;
