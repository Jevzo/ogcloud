const KNOWN_AUDIT_ACTIONS = [
    "GROUP_CREATED",
    "GROUP_UPDATED",
    "GROUP_MAINTENANCE_UPDATED",
    "GROUP_DELETED",
    "GROUP_RESTART_REQUESTED",
    "NETWORK_RESTART_REQUESTED",
    "NETWORK_SETTINGS_UPDATED",
    "NETWORK_MAINTENANCE_UPDATED",
    "RUNTIME_REFRESH_REQUESTED",
    "SERVER_REQUESTED",
    "SERVER_STOP_REQUESTED",
    "SERVER_KILL_REQUESTED",
    "SERVER_TEMPLATE_PUSH_REQUESTED",
    "TEMPLATE_UPLOADED",
    "TEMPLATE_DELETED",
] as const;

const getActionBadgeClassName = (action: string) => {
    const normalizedAction = action.trim().toUpperCase();

    if (
        normalizedAction.includes("DELETE") ||
        normalizedAction.includes("REMOVE") ||
        normalizedAction.includes("REVOKE") ||
        normalizedAction.includes("KILL")
    ) {
        return "border-red-500/30 bg-red-500/10 text-red-300";
    }

    if (
        normalizedAction.includes("CREATE") ||
        normalizedAction.includes("ADD") ||
        normalizedAction.includes("LINK") ||
        normalizedAction.includes("UPLOAD") ||
        normalizedAction.includes("REQUESTED")
    ) {
        return "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";
    }

    return "border-primary/25 bg-primary/10 text-primary";
};

const formatAuditActionLabel = (action: string) =>
    action
        .trim()
        .toLowerCase()
        .split("_")
        .filter(Boolean)
        .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
        .join(" ");

const getAvailableAuditActions = (actions: readonly string[]) =>
    [...new Set([...KNOWN_AUDIT_ACTIONS, ...actions])].sort((left, right) =>
        formatAuditActionLabel(left).localeCompare(formatAuditActionLabel(right)),
    );

export {
    KNOWN_AUDIT_ACTIONS,
    getActionBadgeClassName,
    formatAuditActionLabel,
    getAvailableAuditActions,
};
