import { formatDateTime } from "@/features/servers/lib/server-display";

export const getPlayerStatusBadgeClassName = (online: boolean) =>
    online
        ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
        : "border-border/80 text-muted-foreground";

export const isTemporaryPermissionGrant = (endMillis: number) =>
    Number.isFinite(endMillis) && endMillis > 0;

export const formatPermissionExpiry = (endMillis: number) => {
    if (endMillis === -1) {
        return "Permanent";
    }

    if (!Number.isFinite(endMillis) || endMillis <= 0) {
        return "--";
    }

    return formatDateTime(String(endMillis));
};

export const getPermissionGrantLabel = (endMillis: number) => {
    if (endMillis === -1) {
        return "Permanent access";
    }

    if (isTemporaryPermissionGrant(endMillis)) {
        return "Temporary access";
    }

    return "Unspecified expiry";
};

export const getPermissionGrantBadgeClassName = (endMillis: number) => {
    if (endMillis === -1) {
        return "border-primary/30 bg-primary/10 text-primary";
    }

    if (isTemporaryPermissionGrant(endMillis)) {
        return "border-amber-500/30 bg-amber-500/10 text-amber-300";
    }

    return "border-border/80 text-muted-foreground";
};
