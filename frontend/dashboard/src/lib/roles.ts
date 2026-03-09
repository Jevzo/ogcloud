type RoleLike = string | null | undefined;

const ADMIN_EQUIVALENT_ROLES = new Set(["admin", "service"]);

export const normalizeRole = (role: RoleLike) => role?.trim().toLowerCase() ?? "";

export const hasAdminAccess = (role: RoleLike) =>
    ADMIN_EQUIVALENT_ROLES.has(normalizeRole(role));
