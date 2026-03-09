import * as React from "react";
import { useEffect, useState } from "react";
import { motion } from "motion/react";

import { confirmMinecraftLinkOtp, requestMinecraftLinkOtp } from "@/lib/api";
import { normalizeRole } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";

type LinkStep = "confirm-online" | "offline" | "enter-username" | "enter-otp";

const RequireMinecraftLinkModal = () => {
    const session = useAuthStore((state) => state.session);
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const updateUser = useAuthStore((state) => state.updateUser);
    const logout = useAuthStore((state) => state.logout);

    const [linkStep, setLinkStep] = useState<LinkStep>("confirm-online");
    const [minecraftUsername, setMinecraftUsername] = useState("");
    const [otp, setOtp] = useState("");
    const [linkMessage, setLinkMessage] = useState<string | null>(null);
    const [linkError, setLinkError] = useState<string | null>(null);
    const [isLinkBusy, setIsLinkBusy] = useState(false);

    const normalizedUserRole = normalizeRole(session?.user.role);
    const isServiceRole = normalizedUserRole === "service";
    const linkedPlayerUuid = session?.user.linkedPlayerUuid?.trim() ?? "";
    const isModalOpen = Boolean(session && !isServiceRole && !linkedPlayerUuid);
    const usernamePlaceholder = "Enter your Minecraft username";

    useEffect(() => {
        if (!isModalOpen) {
            setLinkStep("confirm-online");
            setMinecraftUsername("");
            setOtp("");
            setLinkMessage(null);
            setLinkError(null);
            setIsLinkBusy(false);
            return;
        }

        setMinecraftUsername("");
    }, [isModalOpen]);

    const getValidAccessToken = async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    };

    const handleRequestOtp = async (event: React.SubmitEvent<HTMLFormElement>) => {
        event.preventDefault();
        setLinkMessage(null);
        setLinkError(null);

        const nextUsername = minecraftUsername.trim();

        if (!nextUsername) {
            setLinkError("Enter your Minecraft username.");
            return;
        }

        setIsLinkBusy(true);

        try {
            const accessToken = await getValidAccessToken();
            await requestMinecraftLinkOtp(accessToken, nextUsername);
            setLinkStep("enter-otp");
            setLinkMessage("A 6-digit code was sent to your in-game chat.");
        } catch (error) {
            setLinkError(error instanceof Error ? error.message : "Unable to request a link code.");
        } finally {
            setIsLinkBusy(false);
        }
    };

    const handleConfirmOtp = async (event: React.SubmitEvent<HTMLFormElement>) => {
        event.preventDefault();
        setLinkMessage(null);
        setLinkError(null);

        const nextOtp = otp.trim();

        if (!nextOtp) {
            setLinkError("Enter the 6-digit code from Minecraft chat.");
            return;
        }

        setIsLinkBusy(true);

        try {
            const accessToken = await getValidAccessToken();
            const updatedUser = await confirmMinecraftLinkOtp(accessToken, nextOtp);
            updateUser(updatedUser);
            setLinkMessage("Minecraft account linked.");
        } catch (error) {
            setLinkError(
                error instanceof Error ? error.message : "Unable to confirm the link code.",
            );
        } finally {
            setIsLinkBusy(false);
        }
    };

    const handleSignOut = () => {
        if (isLinkBusy) {
            return;
        }

        logout();
    };

    if (!isModalOpen) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-80 flex items-center justify-center bg-slate-950/75 p-4 backdrop-blur-sm">
            <motion.div
                initial={{ y: 12, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.25, ease: "easeOut" }}
                className="w-full max-w-lg rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
            >
                <div className="border-b border-slate-800 px-6 py-4">
                    <h3 className="text-base font-semibold text-white">
                        Connect Minecraft Account
                    </h3>
                    <p className="text-sm text-slate-400">
                        This dashboard is locked until a Minecraft account is linked.
                    </p>
                </div>

                <div className="space-y-5 p-6">
                    {linkStep === "confirm-online" && (
                        <>
                            <div className="rounded-lg border border-slate-800 bg-slate-800/40 px-4 py-4 text-sm text-slate-300">
                                Are you currently online on the Minecraft server with the account
                                you want to link?
                            </div>
                            <div className="flex flex-col gap-3 sm:flex-row">
                                <button
                                    type="button"
                                    onClick={() => setLinkStep("enter-username")}
                                    className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-3 text-sm font-semibold text-slate-950"
                                >
                                    Yes, I&apos;m Online
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setLinkStep("offline")}
                                    className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-3 text-sm font-semibold text-slate-200"
                                >
                                    Not Right Now
                                </button>
                            </div>
                        </>
                    )}

                    {linkStep === "offline" && (
                        <>
                            <div className="rounded-lg border border-amber-500/20 bg-amber-500/10 px-4 py-4 text-sm text-amber-100">
                                The server must deliver a one-time code in Minecraft chat. Come back
                                once you are online in-game.
                            </div>
                            <div className="flex flex-col gap-3 sm:flex-row">
                                <button
                                    type="button"
                                    onClick={() => setLinkStep("confirm-online")}
                                    className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-3 text-sm font-semibold text-slate-950"
                                >
                                    I&apos;m Online Now
                                </button>
                                <button
                                    type="button"
                                    onClick={handleSignOut}
                                    className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-3 text-sm font-semibold text-slate-200"
                                >
                                    Sign Out
                                </button>
                            </div>
                        </>
                    )}

                    {linkStep === "enter-username" && (
                        <form onSubmit={handleRequestOtp} className="space-y-4">
                            <label className="app-field-stack">
                                <span className="app-field-label">Minecraft Username</span>
                                <input
                                    type="text"
                                    value={minecraftUsername}
                                    onChange={(event) => {
                                        setMinecraftUsername(event.target.value);
                                        if (linkError) {
                                            setLinkError(null);
                                        }
                                        if (linkMessage) {
                                            setLinkMessage(null);
                                        }
                                    }}
                                    className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                                    placeholder={usernamePlaceholder}
                                    required
                                />
                            </label>

                            {linkError && (
                                <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                                    {linkError}
                                </div>
                            )}

                            {linkMessage && (
                                <div className="rounded-lg border border-primary/20 bg-primary/10 px-4 py-3 text-sm text-primary">
                                    {linkMessage}
                                </div>
                            )}

                            <div className="flex flex-col gap-3 sm:flex-row">
                                <button
                                    type="submit"
                                    disabled={isLinkBusy}
                                    className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-3 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-70"
                                >
                                    {isLinkBusy ? "Requesting..." : "Send Link Code"}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setLinkStep("confirm-online")}
                                    disabled={isLinkBusy}
                                    className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-3 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-70"
                                >
                                    Back
                                </button>
                            </div>
                        </form>
                    )}

                    {linkStep === "enter-otp" && (
                        <form onSubmit={handleConfirmOtp} className="space-y-4">
                            <div className="rounded-lg border border-primary/10 bg-primary/5 px-4 py-4 text-sm text-slate-300">
                                {linkMessage ??
                                    "A code has been sent to your in-game chat. Enter it below."}
                            </div>

                            <label className="app-field-stack">
                                <span className="app-field-label">6-Digit Code</span>
                                <input
                                    type="text"
                                    value={otp}
                                    onChange={(event) => {
                                        setOtp(event.target.value.replace(/\D/g, "").slice(0, 6));
                                        if (linkError) {
                                            setLinkError(null);
                                        }
                                    }}
                                    className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                                    inputMode="numeric"
                                    required
                                />
                            </label>

                            {linkError && (
                                <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-200">
                                    {linkError}
                                </div>
                            )}

                            <div className="flex flex-col gap-3 sm:flex-row">
                                <button
                                    type="submit"
                                    disabled={isLinkBusy}
                                    className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-3 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-70"
                                >
                                    {isLinkBusy ? "Linking..." : "Confirm Link"}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setLinkStep("enter-username")}
                                    disabled={isLinkBusy}
                                    className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-3 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-70"
                                >
                                    Back
                                </button>
                            </div>
                        </form>
                    )}
                </div>
            </motion.div>
        </div>
    );
};

export default RequireMinecraftLinkModal;
