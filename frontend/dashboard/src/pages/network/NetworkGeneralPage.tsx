import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { FiInfo, FiLock, FiSave } from "react-icons/fi";

import AppSelect from "@/components/AppSelect";
import FieldHintLabel from "@/components/FieldHintLabel";
import { useNetworkPageContext } from "@/pages/network/context";
import { formatNetworkLockDuration, formatProxyRoutingStrategy } from "@/pages/network/utils";
import type { ProxyRoutingStrategy } from "@/types/network";

interface GeneralSettingToggleProps {
    title: string;
    description: string;
    enabled: boolean;
    disabled?: boolean;
    onToggle: () => void;
    titleHint?: string;
}

const GeneralSettingToggle = ({
    description,
    disabled = false,
    enabled,
    onToggle,
    title,
    titleHint,
}: GeneralSettingToggleProps) => (
    <button
        type="button"
        disabled={disabled}
        onClick={onToggle}
        title={titleHint}
        className="app-input-field group flex min-h-16 w-full items-center justify-between rounded-lg border border-slate-700 px-5 py-3 text-left disabled:cursor-not-allowed"
        aria-pressed={enabled}
    >
        <span className="pr-5">
            <span className="block text-sm font-semibold text-slate-100">{title}</span>
            <span className="mt-0.5 block text-xs text-slate-500">{description}</span>
        </span>
        <span
            className={`relative inline-flex h-5 w-9 shrink-0 rounded-full border transition-all duration-500 ease-in-out ${
                enabled
                    ? "border-primary/40 bg-primary/85 group-hover:bg-secondary"
                    : "border-slate-600 bg-slate-700/70 group-hover:bg-slate-700"
            }`}
        >
            <span
                className={`absolute top-1/2 left-0.5 h-4 w-4 rounded-full border border-white/70 bg-white shadow-[0_2px_8px_rgba(15,23,42,0.3)] transition-transform duration-500 ease-in-out ${
                    enabled ? "translate-x-4 -translate-y-1/2" : "translate-x-0 -translate-y-1/2"
                }`}
            />
        </span>
    </button>
);

const NetworkGeneralPage = () => {
    const { isLoading, locks, saveSettings, settings } = useNetworkPageContext();
    const [permissionSystemEnabled, setPermissionSystemEnabled] = useState(true);
    const [tablistEnabled, setTablistEnabled] = useState(true);
    const [proxyRoutingStrategy, setProxyRoutingStrategy] =
        useState<ProxyRoutingStrategy>("LOAD_BASED");
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (!settings) {
            return;
        }

        setPermissionSystemEnabled(settings.general.permissionSystemEnabled);
        setTablistEnabled(settings.general.tablistEnabled);
        setProxyRoutingStrategy(settings.general.proxyRoutingStrategy);
    }, [
        settings?.general.permissionSystemEnabled,
        settings?.general.proxyRoutingStrategy,
        settings?.general.tablistEnabled,
    ]);

    const permissionLock =
        locks.find((lock) => lock.type.toUpperCase() === "PERMISSION_REENABLE") ?? null;
    const recommendedPreset =
        permissionSystemEnabled && tablistEnabled && proxyRoutingStrategy === "LOAD_BASED";

    const handleSave = async () => {
        if (!settings) {
            return;
        }

        setIsSaving(true);

        try {
            await saveSettings(
                {
                    general: {
                        permissionSystemEnabled,
                        tablistEnabled,
                        proxyRoutingStrategy,
                    },
                },
                "General network settings saved.",
            );
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <div className="space-y-6">
            <motion.section
                initial={{ y: 16, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
                className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]"
            >
                <div className="space-y-6">
                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Current Behavior
                            </h3>
                        </div>
                        <div className="space-y-4 p-6">
                            {!settings ? (
                                <p className="text-sm text-slate-400">
                                    {isLoading
                                        ? "Loading general settings..."
                                        : "General settings are unavailable."}
                                </p>
                            ) : (
                                <>
                                    <div className="flex items-center justify-between gap-3 rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                        <div>
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Preset
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {recommendedPreset ? "Recommended" : "Custom"}
                                            </p>
                                        </div>
                                        <span
                                            className={`inline-flex items-center gap-2 rounded-full px-2.5 py-1 text-[11px] font-semibold ${
                                                recommendedPreset
                                                    ? "bg-emerald-500/10 text-emerald-300"
                                                    : "bg-amber-500/10 text-amber-300"
                                            }`}
                                        >
                                            <FiInfo className="h-3.5 w-3.5" />
                                            {recommendedPreset ? "Balanced" : "Modified"}
                                        </span>
                                    </div>

                                    <div className="grid grid-cols-1 gap-3">
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Permission System
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {permissionSystemEnabled ? "Enabled" : "Disabled"}
                                            </p>
                                        </div>
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Tablist
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {tablistEnabled ? "Enabled" : "Disabled"}
                                            </p>
                                        </div>
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Proxy Routing
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {formatProxyRoutingStrategy(proxyRoutingStrategy)}
                                            </p>
                                        </div>
                                    </div>
                                </>
                            )}
                        </div>
                    </div>

                    {permissionLock ? (
                        <div className="rounded-xl border border-amber-500/20 bg-amber-500/10 px-5 py-4 text-sm text-amber-100 shadow-sm">
                            <div className="flex items-start gap-3">
                                <FiLock className="mt-0.5 h-4 w-4 shrink-0 text-amber-300" />
                                <p>
                                    Permission synchronization is still running. You can flip the
                                    permission-system toggle again in{" "}
                                    {formatNetworkLockDuration(permissionLock.ttlSeconds)}.
                                </p>
                            </div>
                        </div>
                    ) : null}
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm self-start">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Editable Configuration
                        </h3>
                    </div>
                    <div className="space-y-6 p-6">
                        {!settings ? (
                            <p className="text-sm text-slate-400">
                                {isLoading
                                    ? "Loading editable general settings..."
                                    : "Editable general settings are unavailable."}
                            </p>
                        ) : (
                            <>
                                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                    <GeneralSettingToggle
                                        title="Permission System"
                                        description="Enable permission injection and updates."
                                        enabled={permissionSystemEnabled}
                                        disabled={Boolean(permissionLock)}
                                        onToggle={() =>
                                            setPermissionSystemEnabled((currentValue) => !currentValue)
                                        }
                                        titleHint={
                                            permissionLock
                                                ? `Wait ${formatNetworkLockDuration(permissionLock.ttlSeconds)} before toggling the permission system again.`
                                                : "Toggle permission-system synchronization."
                                        }
                                    />
                                    <GeneralSettingToggle
                                        title="Tablist"
                                        description="Enable dynamic tablist updates and templates."
                                        enabled={tablistEnabled}
                                        onToggle={() => setTablistEnabled((currentValue) => !currentValue)}
                                    />
                                </div>

                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Load Balancer Player Routing"
                                        hint="Choose between rotating evenly across proxies or routing to the least loaded proxy."
                                    />
                                    <AppSelect
                                        value={proxyRoutingStrategy}
                                        onChangeValue={(value) =>
                                            setProxyRoutingStrategy(value as ProxyRoutingStrategy)
                                        }
                                        disabled={isLoading || !settings}
                                    >
                                        <option value="LOAD_BASED">
                                            Load Based (least connected proxy)
                                        </option>
                                        <option value="ROUND_ROBIN">
                                            Round Robin (next proxy in sequence)
                                        </option>
                                    </AppSelect>
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
                </div>
            </motion.section>
        </div>
    );
};

export default NetworkGeneralPage;
