import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { FiSave } from "react-icons/fi";

import FieldHintLabel from "@/components/FieldHintLabel";
import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { useNetworkPageContext } from "@/pages/network/context";

const NetworkMessagingPage = () => {
    const { isLoading, saveSettings, settings } = useNetworkPageContext();
    const [versionNameGlobal, setVersionNameGlobal] = useState("");
    const [versionNameMaintenance, setVersionNameMaintenance] = useState("");
    const [maintenanceKickMessage, setMaintenanceKickMessage] = useState("");
    const [motdGlobal, setMotdGlobal] = useState("");
    const [motdMaintenance, setMotdMaintenance] = useState("");
    const [tablistHeader, setTablistHeader] = useState("");
    const [tablistFooter, setTablistFooter] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (!settings) {
            return;
        }

        setVersionNameGlobal(settings.versionName.global);
        setVersionNameMaintenance(settings.versionName.maintenance);
        setMaintenanceKickMessage(settings.maintenanceKickMessage);
        setMotdGlobal(settings.motd.global);
        setMotdMaintenance(settings.motd.maintenance);
        setTablistHeader(settings.tablist.header);
        setTablistFooter(settings.tablist.footer);
    }, [
        settings?.maintenanceKickMessage,
        settings?.motd.global,
        settings?.motd.maintenance,
        settings?.tablist.footer,
        settings?.tablist.header,
        settings?.versionName.global,
        settings?.versionName.maintenance,
    ]);

    const tablistEnabled = settings?.general.tablistEnabled ?? true;

    const handleSave = async () => {
        if (!settings) {
            return;
        }

        setIsSaving(true);

        try {
            await saveSettings(
                {
                    maintenanceKickMessage,
                    motd: {
                        global: motdGlobal,
                        maintenance: motdMaintenance,
                    },
                    versionName: {
                        global: versionNameGlobal,
                        maintenance: versionNameMaintenance,
                    },
                    tablist: {
                        header: tablistHeader,
                        footer: tablistFooter,
                    },
                },
                "Messaging settings saved.",
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
                className="grid grid-cols-1 gap-6 xl:grid-cols-2"
            >
                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Connection Messaging
                        </h3>
                    </div>
                    <div className="space-y-6 p-6">
                        {!settings ? (
                            <p className="text-sm text-slate-400">
                                {isLoading
                                    ? "Loading connection messaging..."
                                    : "Connection messaging is unavailable."}
                            </p>
                        ) : (
                            <>
                                <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
                                    <div className="app-field-stack">
                                        <FieldHintLabel
                                            label="Global Version"
                                            hint="Version name shown to players while the network is online."
                                        />
                                        <input
                                            type="text"
                                            value={versionNameGlobal}
                                            onChange={(event) =>
                                                setVersionNameGlobal(event.target.value)
                                            }
                                            disabled={isLoading || !settings}
                                            className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                        />
                                    </div>
                                    <div className="app-field-stack">
                                        <FieldHintLabel
                                            label="Maintenance Version"
                                            hint="Version name shown while maintenance mode is active."
                                        />
                                        <input
                                            type="text"
                                            value={versionNameMaintenance}
                                            onChange={(event) =>
                                                setVersionNameMaintenance(event.target.value)
                                            }
                                            disabled={isLoading || !settings}
                                            className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                        />
                                    </div>
                                </div>

                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Maintenance Kick Message"
                                        hint="Message shown to players disconnected during maintenance mode."
                                    />
                                    <textarea
                                        value={maintenanceKickMessage}
                                        onChange={(event) =>
                                            setMaintenanceKickMessage(event.target.value)
                                        }
                                        disabled={isLoading || !settings}
                                        rows={4}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>

                                <div className="grid grid-cols-1 gap-4">
                                    <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                            Global Version Preview
                                        </p>
                                        <MinecraftTextPreview
                                            value={versionNameGlobal}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                    <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                            Maintenance Version Preview
                                        </p>
                                        <MinecraftTextPreview
                                            value={versionNameMaintenance}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                    <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                            Maintenance Kick Preview
                                        </p>
                                        <MinecraftTextPreview
                                            value={maintenanceKickMessage}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            MOTD
                        </h3>
                    </div>
                    <div className="space-y-6 p-6">
                        {!settings ? (
                            <p className="text-sm text-slate-400">
                                {isLoading
                                    ? "Loading MOTD settings..."
                                    : "MOTD settings are unavailable."}
                            </p>
                        ) : (
                            <>
                                <div className="app-field-stack">
                                    <FieldHintLabel
                                        label="Global"
                                        hint="Primary MOTD shown to players during normal operation."
                                    />
                                    <textarea
                                        value={motdGlobal}
                                        onChange={(event) => setMotdGlobal(event.target.value)}
                                        disabled={isLoading || !settings}
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
                                        value={motdMaintenance}
                                        onChange={(event) => setMotdMaintenance(event.target.value)}
                                        disabled={isLoading || !settings}
                                        rows={4}
                                        className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                    />
                                </div>

                                <div className="grid grid-cols-1 gap-4">
                                    <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                            Global Preview
                                        </p>
                                        <MinecraftTextPreview
                                            value={motdGlobal}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                    <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                            Maintenance Preview
                                        </p>
                                        <MinecraftTextPreview
                                            value={motdMaintenance}
                                            className="mt-2 font-mono"
                                        />
                                    </div>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            </motion.section>

            <motion.section
                initial={{ y: 20, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
                className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
            >
                <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                    <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                        Tablist
                    </h3>
                </div>
                <div className="space-y-6 p-6">
                    {!settings ? (
                        <p className="text-sm text-slate-400">
                            {isLoading
                                ? "Loading tablist configuration..."
                                : "Tablist configuration is unavailable."}
                        </p>
                    ) : (
                        <>
                            <div className="relative">
                                {!tablistEnabled ? (
                                    <div className="pointer-events-none absolute inset-0 z-20 flex items-center justify-center text-center">
                                        <p className="max-w-sm rounded-lg border border-slate-700/70 px-4 py-2 text-sm font-semibold text-amber-200 backdrop-blur-lg">
                                            Tablist configuration is disabled. Enable tablist in
                                            the General section first.
                                        </p>
                                    </div>
                                ) : null}

                                <div
                                    className={`grid grid-cols-1 gap-5 xl:grid-cols-2 ${
                                        !tablistEnabled ? "blur-[8px] select-none" : ""
                                    }`}
                                >
                                    <div className="space-y-5">
                                        <div className="app-field-stack">
                                            <FieldHintLabel
                                                label="Header"
                                                hint="Top section of the tablist, supports Minecraft formatting codes."
                                            />
                                            <textarea
                                                value={tablistHeader}
                                                onChange={(event) =>
                                                    setTablistHeader(event.target.value)
                                                }
                                                disabled={isLoading || !settings || !tablistEnabled}
                                                rows={6}
                                                className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                            />
                                        </div>
                                        <div className="app-field-stack">
                                            <FieldHintLabel
                                                label="Footer"
                                                hint="Bottom section of the tablist, supports Minecraft formatting codes."
                                            />
                                            <textarea
                                                value={tablistFooter}
                                                onChange={(event) =>
                                                    setTablistFooter(event.target.value)
                                                }
                                                disabled={isLoading || !settings || !tablistEnabled}
                                                rows={6}
                                                className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
                                            />
                                        </div>
                                    </div>

                                    <div className="space-y-4">
                                        <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                                Header Preview
                                            </p>
                                            <MinecraftTextPreview
                                                value={tablistHeader}
                                                className="mt-2 font-mono"
                                            />
                                        </div>
                                        <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                            <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                                Footer Preview
                                            </p>
                                            <MinecraftTextPreview
                                                value={tablistFooter}
                                                className="mt-2 font-mono"
                                            />
                                        </div>
                                    </div>
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
        </div>
    );
};

export default NetworkMessagingPage;
