import { motion } from "motion/react";
import { FiActivity, FiServer, FiShield, FiUsers } from "react-icons/fi";

import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { useNetworkPageContext } from "@/pages/network/context";
import { formatProxyRoutingStrategy } from "@/pages/network/utils";

const NetworkOverviewPage = () => {
    const { isLoading, settings, status } = useNetworkPageContext();

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
            meta: settings?.maintenance ? "New joins are restricted" : "Network open for players",
            icon: FiShield,
        },
    ] as const;

    return (
        <div className="space-y-6">
            <motion.section
                initial={{ y: 16, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
                className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4"
            >
                {statusCards.map(({ title, value, suffix, tone, meta, icon: Icon }) => (
                    <div
                        key={title}
                        className="rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-sm"
                    >
                        <div className="mb-4 flex items-start justify-between">
                            <p className="text-sm font-medium text-slate-400">{title}</p>
                            <Icon className={`h-5 w-5 ${tone}`} />
                        </div>
                        <div>
                            <h3 className="text-3xl font-bold tracking-tight text-white">
                                {isLoading ? "--" : value}{" "}
                                {suffix ? (
                                    <span className="text-lg font-medium text-slate-500">
                                        {suffix}
                                    </span>
                                ) : null}
                            </h3>
                            <p className={`mt-2 text-xs font-medium uppercase tracking-wide ${tone}`}>
                                {meta}
                            </p>
                        </div>
                    </div>
                ))}
            </motion.section>

            <motion.section
                initial={{ y: 20, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut", delay: 0.04 }}
                className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]"
            >
                <div className="space-y-6">
                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Current Network Values
                            </h3>
                        </div>
                        <div className="p-6">
                            {!settings ? (
                                <p className="text-sm text-slate-400">
                                    {isLoading
                                        ? "Loading network configuration..."
                                        : "Network settings are unavailable."}
                                </p>
                            ) : (
                                <div className="space-y-5">
                                    <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Max Slots
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {settings.maxPlayers}
                                            </p>
                                        </div>
                                        <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                            <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                                Default Group
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {settings.defaultGroup}
                                            </p>
                                        </div>
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
                                                Routing Strategy
                                            </p>
                                            <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                                {formatProxyRoutingStrategy(
                                                    settings.general.proxyRoutingStrategy,
                                                )}
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
                                                    settings.general.permissionSystemEnabled
                                                        ? "bg-emerald-500/12 text-emerald-300"
                                                        : "bg-slate-700/40 text-slate-300"
                                                }`}
                                            >
                                                Permission System:{" "}
                                                {settings.general.permissionSystemEnabled
                                                    ? "Enabled"
                                                    : "Disabled"}
                                            </span>
                                            <span
                                                className={`inline-flex rounded-full px-2.5 py-1 font-semibold ${
                                                    settings.general.tablistEnabled
                                                        ? "bg-emerald-500/12 text-emerald-300"
                                                        : "bg-slate-700/40 text-slate-300"
                                                }`}
                                            >
                                                Tablist:{" "}
                                                {settings.general.tablistEnabled
                                                    ? "Enabled"
                                                    : "Disabled"}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                MOTD Snapshot
                            </h3>
                        </div>
                        <div className="grid grid-cols-1 gap-4 p-6">
                            <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Global
                                </p>
                                <MinecraftTextPreview
                                    value={settings?.motd.global}
                                    className="mt-2 font-mono"
                                />
                            </div>
                            <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Maintenance
                                </p>
                                <MinecraftTextPreview
                                    value={settings?.motd.maintenance}
                                    className="mt-2 font-mono"
                                />
                            </div>
                        </div>
                    </div>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm self-start">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Connection Messaging
                        </h3>
                    </div>
                    <div className="space-y-5 p-6">
                        {!settings ? (
                            <p className="text-sm text-slate-400">
                                {isLoading
                                    ? "Loading player-facing connection text..."
                                    : "Connection messaging is unavailable."}
                            </p>
                        ) : (
                            <>
                                <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Global Version
                                    </p>
                                    <MinecraftTextPreview
                                        value={settings.versionName.global}
                                        className="mt-2 font-mono"
                                    />
                                </div>
                                <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Maintenance Version
                                    </p>
                                    <MinecraftTextPreview
                                        value={settings.versionName.maintenance}
                                        className="mt-2 font-mono"
                                    />
                                </div>
                                <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Maintenance Kick Message
                                    </p>
                                    <MinecraftTextPreview
                                        value={settings.maintenanceKickMessage}
                                        className="mt-2 font-mono"
                                    />
                                </div>
                            </>
                        )}
                    </div>
                </div>
            </motion.section>
        </div>
    );
};

export default NetworkOverviewPage;
