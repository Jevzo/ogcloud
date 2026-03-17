import type { NetworkLockRecord, ProxyRoutingStrategy } from "@/types/network";

const capitalizeWord = (value: string) =>
    value.charAt(0).toUpperCase() + value.slice(1).toLowerCase();

export const createRestartConfirmationCode = () =>
    `${Math.floor(100000 + Math.random() * 900000)}`;

export const formatProxyRoutingStrategy = (strategy: ProxyRoutingStrategy) =>
    strategy === "ROUND_ROBIN" ? "Round Robin" : "Load Based";

export const formatNetworkLockType = (type: string) =>
    type
        .split("_")
        .map(capitalizeWord)
        .join(" ");

export const formatNetworkLockDuration = (ttlSeconds: number | null) => {
    if (ttlSeconds === null || !Number.isFinite(ttlSeconds)) {
        return "No expiry";
    }

    const totalSeconds = Math.max(0, Math.floor(ttlSeconds));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    if (minutes === 0) {
        return `${seconds}s`;
    }

    if (seconds === 0) {
        return `${minutes}m`;
    }

    return `${minutes}m ${String(seconds).padStart(2, "0")}s`;
};

export const getNetworkLockSummary = (lock: NetworkLockRecord) => {
    switch (lock.type) {
        case "NETWORK_RESTART":
            return "A full network restart is still in progress.";
        case "GROUP_RESTART":
            return lock.targetId
                ? `${lock.targetId} is still within its restart cooldown window.`
                : "A group restart is still within its cooldown window.";
        case "PERMISSION_REENABLE":
            return "Permission-system synchronization is still running.";
        default:
            return "This synchronization lock is still active.";
    }
};
