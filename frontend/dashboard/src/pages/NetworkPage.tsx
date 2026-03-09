import {useCallback, useEffect, useRef, useState} from "react";
import {motion} from "motion/react";
import {FiActivity, FiAlertTriangle, FiGlobe, FiInfo, FiSave, FiServer, FiShield, FiUsers, FiX,} from "react-icons/fi";

import AppNumberInput from "@/components/AppNumberInput";
import AppSelect from "@/components/AppSelect";
import FieldHintLabel from "@/components/FieldHintLabel";
import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import AppToasts from "@/components/AppToasts";
import {
    getNetworkSettings,
    getNetworkStatus,
    listGroups,
    toggleNetworkMaintenance,
    updateNetworkSettings,
} from "@/lib/api";
import {useAuthStore} from "@/store/auth-store";
import {useNetworkSettingsStore} from "@/store/network-settings-store";
import type {GroupListItem} from "@/types/dashboard";
import type {NetworkSettingsRecord, NetworkStatusRecord, ProxyRoutingStrategy,} from "@/types/network";

interface NetworkFormValues {
    maxPlayers: string;
    defaultGroup: string;
    maintenanceKickMessage: string;
    motdGlobal: string;
    motdMaintenance: string;
    versionNameGlobal: string;
    versionNameMaintenance: string;
    tablistHeader: string;
    tablistFooter: string;
    permissionSystemEnabled: boolean;
    tablistEnabled: boolean;
    proxyRoutingStrategy: ProxyRoutingStrategy;
}

const DEFAULT_STATUS: NetworkStatusRecord = {
    onlinePlayers: 0,
    serverCount: 0,
    proxyCount: 0,
};
const REFRESH_INTERVAL_MS = 10_000;

const createFormValues = (settings: NetworkSettingsRecord): NetworkFormValues => ({
    maxPlayers: String(settings.maxPlayers),
    defaultGroup: settings.defaultGroup,
    maintenanceKickMessage: settings.maintenanceKickMessage,
    motdGlobal: settings.motd.global,
    motdMaintenance: settings.motd.maintenance,
    versionNameGlobal: settings.versionName.global,
    versionNameMaintenance: settings.versionName.maintenance,
    tablistHeader: settings.tablist.header,
    tablistFooter: settings.tablist.footer,
    permissionSystemEnabled: settings.general.permissionSystemEnabled,
    tablistEnabled: settings.general.tablistEnabled,
    proxyRoutingStrategy: settings.general.proxyRoutingStrategy,
});

const areFormValuesEqual = (
    left: NetworkFormValues | null,
    right: NetworkFormValues | null
) => JSON.stringify(left) === JSON.stringify(right);

const NetworkPage = () => {
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const setGeneralSettings = useNetworkSettingsStore((state) => state.setGeneral);

    const [settings, setSettings] = useState<NetworkSettingsRecord | null>(null);
    const [status, setStatus] = useState<NetworkStatusRecord>(DEFAULT_STATUS);
    const [groups, setGroups] = useState<GroupListItem[]>([]);
    const [formValues, setFormValues] = useState<NetworkFormValues | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isTogglingMaintenance, setIsTogglingMaintenance] = useState(false);
    const [isMaintenanceModalOpen, setIsMaintenanceModalOpen] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const settingsRef = useRef<NetworkSettingsRecord | null>(null);
    const formValuesRef = useRef<NetworkFormValues | null>(null);

    useEffect(() => {
        settingsRef.current = settings;
    }, [settings]);

    useEffect(() => {
        formValuesRef.current = formValues;
    }, [formValues]);

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadNetworkData = useCallback(async (showLoading = true) => {
        if (showLoading) {
            setIsLoading(true);
        }

        try {
            const accessToken = await getValidAccessToken();
            const [nextSettings, nextStatus, nextGroups] = await Promise.all([
                getNetworkSettings(accessToken),
                getNetworkStatus(accessToken),
                listGroups(accessToken),
            ]);

            setSettings(nextSettings);
            setStatus(nextStatus);
            setGroups(nextGroups);
            setGeneralSettings(nextSettings.general);
            const nextFormValues = createFormValues(nextSettings);
            setFormValues((currentValue) => {
                const currentSettingsValues = settingsRef.current
                    ? createFormValues(settingsRef.current)
                    : null;
                const liveFormValues = currentValue ?? formValuesRef.current;
                const isDirty = currentValue && !areFormValuesEqual(currentValue, currentSettingsValues);

                if (
                    !liveFormValues ||
                    !isDirty ||
                    showLoading ||
                    isSaving ||
                    isTogglingMaintenance
                ) {
                    return nextFormValues;
                }

                return liveFormValues;
            });
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load network settings."
            );
        } finally {
            setIsLoading(false);
        }
    }, [getValidAccessToken, isSaving, isTogglingMaintenance, setGeneralSettings]);

    useEffect(() => {
        let active = true;

        const runLoad = async () => {
            if (!active) {
                return;
            }

            await loadNetworkData(true);
        };

        void runLoad();

        const intervalId = window.setInterval(() => {
            if (!active) {
                return;
            }

            void loadNetworkData(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            active = false;
            window.clearInterval(intervalId);
        };
    }, [loadNetworkData]);

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

    const setField = (field: keyof NetworkFormValues, value: string) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                    ...currentValue,
                    [field]: value,
                }
                : currentValue
        );
    };

    const setBooleanField = (
        field: Extract<keyof NetworkFormValues, "permissionSystemEnabled" | "tablistEnabled">,
        value: boolean
    ) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                    ...currentValue,
                    [field]: value,
                }
                : currentValue
        );
    };

    const setRoutingStrategy = (value: ProxyRoutingStrategy) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                    ...currentValue,
                    proxyRoutingStrategy: value,
                }
                : currentValue
        );
    };

    const handleToggleMaintenance = async () => {
        if (!settings) {
            return;
        }

        if (!settings.maintenance) {
            setIsMaintenanceModalOpen(true);
            return;
        }

        setIsTogglingMaintenance(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const nextSettings = await toggleNetworkMaintenance(accessToken, false);

            setSettings(nextSettings);
            setGeneralSettings(nextSettings.general);
            setFormValues(createFormValues(nextSettings));
            setSuccessMessage("Network maintenance has been disabled.");
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Unable to update network maintenance."
            );
        } finally {
            setIsTogglingMaintenance(false);
        }
    };

    const handleConfirmEnableMaintenance = async () => {
        if (!settings) {
            return;
        }

        setIsTogglingMaintenance(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const nextSettings = await toggleNetworkMaintenance(accessToken, true);

            setSettings(nextSettings);
            setGeneralSettings(nextSettings.general);
            setFormValues(createFormValues(nextSettings));
            setSuccessMessage("Network maintenance has been enabled.");
            setIsMaintenanceModalOpen(false);
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Unable to update network maintenance."
            );
        } finally {
            setIsTogglingMaintenance(false);
        }
    };

    const handleSave = async () => {
        if (!formValues || !settings) {
            return;
        }

        const parsedMaxPlayers = Number.parseInt(formValues.maxPlayers, 10);

        if (!Number.isFinite(parsedMaxPlayers) || parsedMaxPlayers <= 0) {
            setErrorMessage("Max players must be a positive number.");
            return;
        }

        const normalizedDefaultGroup = formValues.defaultGroup.trim();
        const hasEligibleDefaultGroup = eligibleDefaultGroups.some(
            (group) => group.id === normalizedDefaultGroup
        );

        if (!normalizedDefaultGroup || !hasEligibleDefaultGroup) {
            setErrorMessage("Choose a non-proxy default group.");
            return;
        }

        setIsSaving(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const nextSettings = await updateNetworkSettings(accessToken, {
                maxPlayers: parsedMaxPlayers,
                defaultGroup: normalizedDefaultGroup,
                maintenanceKickMessage: formValues.maintenanceKickMessage,
                motd: {
                    global: formValues.motdGlobal,
                    maintenance: formValues.motdMaintenance,
                },
                versionName: {
                    global: formValues.versionNameGlobal,
                    maintenance: formValues.versionNameMaintenance,
                },
                tablist: {
                    header: formValues.tablistHeader,
                    footer: formValues.tablistFooter,
                },
                general: {
                    permissionSystemEnabled: formValues.permissionSystemEnabled,
                    tablistEnabled: formValues.tablistEnabled,
                    proxyRoutingStrategy: formValues.proxyRoutingStrategy,
                },
            });

            setSettings(nextSettings);
            setGeneralSettings(nextSettings.general);
            setFormValues(createFormValues(nextSettings));
            setSuccessMessage("Network settings saved.");
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to save network settings."
            );
        } finally {
            setIsSaving(false);
        }
    };

    const statusCards = [
        {
            title: "Online Players",
            value: `${status.onlinePlayers}`,
            suffix: null,
            tone: "text-emerald-400",
            meta: "Across all connected proxies",
            icon: FiUsers,
        },
        {
            title: "Game Servers",
            value: `${status.serverCount}`,
            suffix: "Live",
            tone: "text-primary",
            meta: "Non-proxy runtime instances",
            icon: FiServer,
        },
        {
            title: "Proxies",
            value: `${status.proxyCount}`,
            suffix: "Online",
            tone: "text-primary",
            meta: "Connected gateway nodes",
            icon: FiActivity,
        },
        {
            title: "Maintenance",
            value: settings?.maintenance ? "Enabled" : "Disabled",
            suffix: null,
            tone: settings?.maintenance ? "text-amber-300" : "text-slate-400",
            meta: settings?.maintenance
                ? "New joins are restricted"
                : "Network open for players",
            icon: FiShield,
        },
    ] as const;
    const eligibleDefaultGroups = groups.filter(
        (group) => group.type.toUpperCase() !== "PROXY"
    );
    const defaultGroupValue =
        formValues && eligibleDefaultGroups.some((group) => group.id === formValues.defaultGroup)
            ? formValues.defaultGroup
            : "";
    const tablistEnabled = formValues?.tablistEnabled ?? true;
    const permissionSystemEnabled = formValues?.permissionSystemEnabled ?? true;
    const proxyRoutingStrategy = formValues?.proxyRoutingStrategy ?? "LOAD_BASED";

    return (
        <div className="space-y-8">
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                            {
                                id: "network-error",
                                message: errorMessage,
                                onDismiss: () => setErrorMessage(null),
                                tone: "error" as const,
                            },
                        ]
                        : []),
                    ...(successMessage
                        ? [
                            {
                                id: "network-success",
                                message: successMessage,
                                onDismiss: () => setSuccessMessage(null),
                                tone: "success" as const,
                            },
                        ]
                        : []),
                ]}
            />

            <motion.section
                initial={{y: 12, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut"}}
                className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
            >
                <div>
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiGlobe className="h-5 w-5 text-primary"/>
                        Network
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Configure global MOTD, join routing, tablist copy, and maintenance
                        behavior for the whole network.
                    </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <button
                        type="button"
                        onClick={() => void handleToggleMaintenance()}
                        disabled={!settings || isTogglingMaintenance}
                        className={`app-button-field button-hover-lift inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${
                            settings?.maintenance
                                ? "button-shadow-neutral border border-slate-700 text-slate-200"
                                : "button-shadow-warning bg-amber-500/12 text-amber-300"
                        }`}
                    >
                        <FiShield className="h-4 w-4"/>
                        {isTogglingMaintenance
                            ? "Updating..."
                            : settings?.maintenance
                                ? "Disable Maintenance"
                                : "Enable Maintenance"}
                    </button>
                </div>
            </motion.section>

            <motion.section
                initial={{y: 16, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut", delay: 0.05}}
                className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4"
            >
                {statusCards.map(({title, value, suffix, tone, meta, icon: Icon}) => (
                    <div
                        key={title}
                        className="rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-sm"
                    >
                        <div className="mb-4 flex items-start justify-between">
                            <p className="text-sm font-medium text-slate-400">{title}</p>
                            <Icon className={`h-5 w-5 ${tone}`}/>
                        </div>
                        <div>
                            <h3 className="text-3xl font-bold tracking-tight text-white">
                                {isLoading ? "--" : value}{" "}
                                {suffix && (
                                    <span className="text-lg font-medium text-slate-500">{suffix}</span>
                                )}
                            </h3>
                            <p className={`mt-2 text-xs font-medium uppercase tracking-wide ${tone}`}>
                                {meta}
                            </p>
                        </div>
                    </div>
                ))}
            </motion.section>

            <motion.section
                initial={{y: 20, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut", delay: 0.09}}
                className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]"
            >
                <div className="space-y-6">
                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Current Network Values
                            </h3>
                        </div>
                        <div className="space-y-5 p-6">
                            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                    <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                        Max Slots
                                    </p>
                                    <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                        {settings?.maxPlayers ?? "--"}
                                    </p>
                                </div>
                                <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                    <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                        Default Group
                                    </p>
                                    <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                        {settings?.defaultGroup || "--"}
                                    </p>
                                </div>
                                <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                    <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                        Maintenance
                                    </p>
                                    <p
                                        className={`mt-1.5 text-sm font-semibold ${
                                            settings?.maintenance ? "text-amber-300" : "text-emerald-300"
                                        }`}
                                    >
                                        {settings ? (settings.maintenance ? "Enabled" : "Disabled") : "--"}
                                    </p>
                                </div>
                                <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                    <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                        Routing Strategy
                                    </p>
                                    <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                        {settings?.general.proxyRoutingStrategy === "ROUND_ROBIN"
                                            ? "Round Robin"
                                            : "Load Based"}
                                    </p>
                                </div>
                            </div>

                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Plugin Controls
                                </p>
                                <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                  <span
                      className={`inline-flex rounded-full px-2.5 py-1 font-semibold ${
                          settings?.general.permissionSystemEnabled
                              ? "bg-emerald-500/12 text-emerald-300"
                              : "bg-slate-700/40 text-slate-300"
                      }`}
                  >
                    Permission System:{" "}
                      {settings?.general.permissionSystemEnabled ? "Enabled" : "Disabled"}
                  </span>
                                    <span
                                        className={`inline-flex rounded-full px-2.5 py-1 font-semibold ${
                                            settings?.general.tablistEnabled
                                                ? "bg-emerald-500/12 text-emerald-300"
                                                : "bg-slate-700/40 text-slate-300"
                                        }`}
                                    >
                    Tablist: {settings?.general.tablistEnabled ? "Enabled" : "Disabled"}
                  </span>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                MOTD Snapshot
                            </h3>
                        </div>
                        <div className="grid grid-cols-1 gap-4 p-6">
                            <div
                                className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Global
                                </p>
                                <MinecraftTextPreview value={settings?.motd.global} className="mt-2 font-mono"/>
                            </div>
                            <div
                                className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Maintenance
                                </p>
                                <MinecraftTextPreview value={settings?.motd.maintenance} className="mt-2 font-mono"/>
                            </div>
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Version Snapshot
                            </h3>
                        </div>
                        <div className="space-y-5 p-6">
                            <div
                                className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Global Version
                                </p>
                                <MinecraftTextPreview value={settings?.versionName.global} className="mt-2 font-mono"/>
                            </div>
                            <div
                                className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Maintenance Version
                                </p>
                                <MinecraftTextPreview
                                    value={settings?.versionName.maintenance}
                                    className="mt-2 font-mono"
                                />
                            </div>
                            <div
                                className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Maintenance Kick Message
                                </p>
                                <MinecraftTextPreview
                                    value={settings?.maintenanceKickMessage}
                                    className="mt-2 font-mono"
                                />
                            </div>
                        </div>
                    </div>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm self-start">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Editable Configuration
                        </h3>
                    </div>
                    <div className="space-y-6 p-6">
                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <h4 className="text-sm font-semibold text-slate-200">Core Settings</h4>
                            <div className="mt-4 grid grid-cols-1 gap-5 md:grid-cols-2">
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Max Players"
                                        hint="Maximum player slots shown and enforced across the whole network."
                                    />
                                    <AppNumberInput
                                        value={formValues?.maxPlayers ?? ""}
                                        onChangeValue={(value) => setField("maxPlayers", value)}
                                        min={1}
                                        disabled={isLoading || !formValues}
                                    />
                                </div>

                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Default Group"
                                        hint="Fallback game group used when no explicit target is provided."
                                    />
                                    <AppSelect
                                        value={defaultGroupValue}
                                        onChangeValue={(value) => setField("defaultGroup", value)}
                                        disabled={isLoading || !formValues || eligibleDefaultGroups.length === 0}
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

                                <div className="app-field-stack md:col-span-2">
                                    <FieldHintLabel
                                        label="Maintenance Kick Message"
                                        hint="Message shown to players disconnected during maintenance mode."
                                    />
                                    <textarea
                                        value={formValues?.maintenanceKickMessage ?? ""}
                                        onChange={(event) =>
                                            setField("maintenanceKickMessage", event.target.value)
                                        }
                                        disabled={isLoading || !formValues}
                                        rows={4}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                                <div>
                                    <h4 className="text-sm font-semibold text-slate-200">General Plugin Settings</h4>
                                    <p className="mt-1 text-xs text-slate-500">
                                        Control global permission and tablist handling across plugins.
                                    </p>
                                </div>
                                <span
                                    className={`inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                                        permissionSystemEnabled && tablistEnabled && proxyRoutingStrategy === "LOAD_BASED"
                                            ? "bg-emerald-500/10 text-emerald-300"
                                            : "bg-amber-500/10 text-amber-300"
                                    }`}
                                >
                  <FiInfo className="h-3.5 w-3.5"/>
                                    {permissionSystemEnabled && tablistEnabled && proxyRoutingStrategy === "LOAD_BASED"
                                        ? "Recommended"
                                        : "Custom"}
                </span>
                            </div>

                            <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2">
                                <button
                                    type="button"
                                    disabled={isLoading || !formValues}
                                    onClick={() =>
                                        setBooleanField("permissionSystemEnabled", !permissionSystemEnabled)
                                    }
                                    className="app-input-field group flex min-h-16 w-full items-center justify-between rounded-lg border border-slate-700 px-5 py-3 text-left disabled:cursor-not-allowed"
                                    aria-pressed={permissionSystemEnabled}
                                >
                  <span className="pr-5">
                    <span className="block text-sm font-semibold text-slate-100">Permission System</span>
                    <span className="mt-0.5 block text-xs text-slate-500">
                      Enable permission injection and updates.
                    </span>
                  </span>
                                    <span
                                        className={`relative inline-flex h-5 w-9 shrink-0 rounded-full border transition-all duration-500 ease-in-out ${
                                            permissionSystemEnabled
                                                ? "border-primary/40 bg-primary/85 group-hover:bg-secondary"
                                                : "border-slate-600 bg-slate-700/70 group-hover:bg-slate-700"
                                        }`}
                                    >
                    <span
                        className={`absolute top-1/2 left-0.5 h-4 w-4 rounded-full border border-white/70 bg-white shadow-[0_2px_8px_rgba(15,23,42,0.3)] transition-transform duration-500 ease-in-out ${
                            permissionSystemEnabled
                                ? "translate-x-4 -translate-y-1/2"
                                : "translate-x-0 -translate-y-1/2"
                        }`}
                    />
                  </span>
                                </button>

                                <button
                                    type="button"
                                    disabled={isLoading || !formValues}
                                    onClick={() => setBooleanField("tablistEnabled", !tablistEnabled)}
                                    className="app-input-field group flex min-h-16 w-full items-center justify-between rounded-lg border border-slate-700 px-5 py-3 text-left disabled:cursor-not-allowed"
                                    aria-pressed={tablistEnabled}
                                >
                  <span className="pr-5">
                    <span className="block text-sm font-semibold text-slate-100">Tablist</span>
                    <span className="mt-0.5 block text-xs text-slate-500">
                      Enable dynamic tablist updates and templates.
                    </span>
                  </span>
                                    <span
                                        className={`relative inline-flex h-5 w-9 shrink-0 rounded-full border transition-all duration-500 ease-in-out ${
                                            tablistEnabled
                                                ? "border-primary/40 bg-primary/85 group-hover:bg-secondary"
                                                : "border-slate-600 bg-slate-700/70 group-hover:bg-slate-700"
                                        }`}
                                    >
                    <span
                        className={`absolute top-1/2 left-0.5 h-4 w-4 rounded-full border border-white/70 bg-white shadow-[0_2px_8px_rgba(15,23,42,0.3)] transition-transform duration-500 ease-in-out ${
                            tablistEnabled
                                ? "translate-x-4 -translate-y-1/2"
                                : "translate-x-0 -translate-y-1/2"
                        }`}
                    />
                  </span>
                                </button>
                            </div>

                            <div className="mt-4 grid grid-cols-1 gap-5">
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Load Balancer Player Routing"
                                        hint="Choose between rotating evenly across proxies or routing to the least loaded proxy."
                                    />
                                    <AppSelect
                                        value={proxyRoutingStrategy}
                                        onChangeValue={(value) => setRoutingStrategy(value as ProxyRoutingStrategy)}
                                        disabled={isLoading || !formValues}
                                    >
                                        <option value="LOAD_BASED">
                                            Load Based (least connected proxy)
                                        </option>
                                        <option value="ROUND_ROBIN">
                                            Round Robin (next proxy in sequence)
                                        </option>
                                    </AppSelect>
                                </div>
                            </div>
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <h4 className="text-sm font-semibold text-slate-200">Version Name</h4>
                            <div className="mt-4 grid grid-cols-1 gap-5 md:grid-cols-2">
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Global"
                                        hint="Version name shown to players while the network is online."
                                    />
                                    <input
                                        type="text"
                                        value={formValues?.versionNameGlobal ?? ""}
                                        onChange={(event) =>
                                            setField("versionNameGlobal", event.target.value)
                                        }
                                        disabled={isLoading || !formValues}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Maintenance"
                                        hint="Version name shown while maintenance mode is active."
                                    />
                                    <input
                                        type="text"
                                        value={formValues?.versionNameMaintenance ?? ""}
                                        onChange={(event) =>
                                            setField("versionNameMaintenance", event.target.value)
                                        }
                                        disabled={isLoading || !formValues}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <h4 className="text-sm font-semibold text-slate-200">MOTD</h4>
                            <div className="mt-4 grid grid-cols-1 gap-5">
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Global"
                                        hint="Primary MOTD shown to players during normal operation."
                                    />
                                    <textarea
                                        value={formValues?.motdGlobal ?? ""}
                                        onChange={(event) => setField("motdGlobal", event.target.value)}
                                        disabled={isLoading || !formValues}
                                        rows={4}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Maintenance"
                                        hint="MOTD shown to players while maintenance mode is enabled."
                                    />
                                    <textarea
                                        value={formValues?.motdMaintenance ?? ""}
                                        onChange={(event) =>
                                            setField("motdMaintenance", event.target.value)
                                        }
                                        disabled={isLoading || !formValues}
                                        rows={4}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>
                            </div>
                        </div>

                        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
                            <h4 className="text-sm font-semibold text-slate-200">Tablist</h4>
                            <div className="relative mt-4">
                                {!tablistEnabled ? (
                                    <div
                                        className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center text-center">
                                        <p className="max-w-sm rounded-lg border border-slate-700/70 px-4 py-2 text-sm font-semibold text-amber-200 backdrop-blur-lg">
                                            Tablist configuration is disabled. Enable tablist in General Plugin
                                            Settings.
                                        </p>
                                    </div>
                                ) : null}

                                <div
                                    className={`space-y-5 transition ${!tablistEnabled ? "blur-[8px] select-none" : ""}`}>
                                    <div className="app-field-stack">
                                        <FieldHintLabel
                                            label="Header"
                                            hint="Top section of the tablist, supports Minecraft formatting codes."
                                        />
                                        <textarea
                                            value={formValues?.tablistHeader ?? ""}
                                            onChange={(event) => setField("tablistHeader", event.target.value)}
                                            disabled={isLoading || !formValues || !tablistEnabled}
                                            rows={5}
                                            className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                        />
                                    </div>
                                    <div className="app-field-stack">
                                        <FieldHintLabel
                                            label="Footer"
                                            hint="Bottom section of the tablist, supports Minecraft formatting codes."
                                        />
                                        <textarea
                                            value={formValues?.tablistFooter ?? ""}
                                            onChange={(event) => setField("tablistFooter", event.target.value)}
                                            disabled={isLoading || !formValues || !tablistEnabled}
                                            rows={5}
                                            className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                        />
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end border-t border-slate-800/70 pt-5">
                            <button
                                type="button"
                                onClick={() => void handleSave()}
                                disabled={!formValues || isSaving}
                                className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                <FiSave className="h-4 w-4"/>
                                {isSaving ? "Saving..." : "Save Changes"}
                            </button>
                        </div>
                    </div>
                </div>
            </motion.section>

            {isMaintenanceModalOpen && (
                <div
                    className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                    <motion.div
                        initial={{y: 12, opacity: 0}}
                        animate={{y: 0, opacity: 1}}
                        transition={{duration: 0.25, ease: "easeOut"}}
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
                                <FiX className="h-4 w-4"/>
                            </button>
                        </div>

                        <div className="space-y-4 px-6 py-5">
                            <div
                                className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-4 text-sm text-red-100">
                                <div className="flex items-start gap-3">
                                    <FiAlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300"/>
                                    <p>
                                        Enabling maintenance will remove all players from the network
                                        and block normal joins until you disable it again.
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
            )}
        </div>
    );
};

export default NetworkPage;
