import type { AuthSession } from "@/types/auth";

const AUTH_STORAGE_KEY = "ogcloud.dashboard.auth-session";

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null;

const isAuthSession = (value: unknown): value is AuthSession => {
  if (!isRecord(value) || !isRecord(value.user)) {
    return false;
  }

  return (
    typeof value.accessToken === "string" &&
    typeof value.accessTokenExpiresAt === "string" &&
    typeof value.refreshToken === "string" &&
    typeof value.refreshTokenExpiresAt === "string" &&
    typeof value.user.id === "string" &&
    typeof value.user.email === "string" &&
    typeof value.user.username === "string" &&
    typeof value.user.role === "string" &&
    (typeof value.user.linkedPlayerUuid === "string" ||
      value.user.linkedPlayerUuid === null)
  );
};

const getStorage = () => {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return window.localStorage;
  } catch {
    return null;
  }
};

export const loadAuthSession = (): AuthSession | null => {
  const storage = getStorage();

  if (!storage) {
    return null;
  }

  const rawValue = storage.getItem(AUTH_STORAGE_KEY);

  if (!rawValue) {
    return null;
  }

  try {
    const parsedValue = JSON.parse(rawValue);

    if (isAuthSession(parsedValue)) {
      return parsedValue;
    }
  } catch {
    storage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }

  storage.removeItem(AUTH_STORAGE_KEY);
  return null;
};

export const saveAuthSession = (session: AuthSession) => {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  try {
    storage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
  } catch {
    return;
  }
};

export const clearAuthSession = () => {
  const storage = getStorage();

  if (!storage) {
    return;
  }

  storage.removeItem(AUTH_STORAGE_KEY);
};
