import { create } from "zustand";

import {
  clearAuthSession,
  loadAuthSession,
  saveAuthSession,
} from "@/lib/auth-storage";
import { loginWithEmailPassword, refreshSessionToken } from "@/lib/api";
import type { AuthSession, AuthUser, LoginCredentials } from "@/types/auth";

type AuthStatus =
  | "anonymous"
  | "authenticating"
  | "authenticated"
  | "refreshing";

interface AuthState {
  status: AuthStatus;
  session: AuthSession | null;
  login: (credentials: LoginCredentials) => Promise<AuthSession>;
  setSession: (session: AuthSession) => void;
  logout: () => void;
  refreshIfNeeded: () => Promise<AuthSession | null>;
  updateUser: (user: AuthUser) => void;
}

const initialSession = loadAuthSession();
const SESSION_STALE_BUFFER_MS = 60_000;
let refreshInFlight: Promise<AuthSession | null> | null = null;

const isSessionStale = (
  session: AuthSession,
  bufferMs = SESSION_STALE_BUFFER_MS
) => {
  const expiresAt = new Date(session.accessTokenExpiresAt).getTime();
  return Number.isNaN(expiresAt) || expiresAt <= Date.now() + bufferMs;
};

export const useAuthStore = create<AuthState>((set, get) => ({
  status: initialSession ? "authenticated" : "anonymous",
  session: initialSession,
  async login(credentials) {
    set({ status: "authenticating" });

    try {
      const session = await loginWithEmailPassword(credentials);
      saveAuthSession(session);
      set({ status: "authenticated", session });
      return session;
    } catch (error) {
      clearAuthSession();
      set({ status: "anonymous", session: null });
      throw error;
    }
  },
  setSession(session) {
    saveAuthSession(session);
    set({ status: "authenticated", session });
  },
  logout() {
    refreshInFlight = null;
    clearAuthSession();
    set({ status: "anonymous", session: null });
  },
  async refreshIfNeeded() {
    const currentSession = get().session;

    if (!currentSession) {
      return null;
    }

    if (!isSessionStale(currentSession)) {
      return currentSession;
    }

    if (refreshInFlight) {
      return refreshInFlight;
    }

    set({ status: "refreshing" });
    const refreshToken = currentSession.refreshToken;
    refreshInFlight = (async () => {
      try {
        const nextSession = await refreshSessionToken(refreshToken);
        const activeSession = get().session;

        if (!activeSession || activeSession.refreshToken !== refreshToken) {
          return activeSession;
        }

        saveAuthSession(nextSession);
        set({ status: "authenticated", session: nextSession });
        return nextSession;
      } catch (error) {
        const activeSession = get().session;

        if (activeSession?.refreshToken === refreshToken) {
          clearAuthSession();
          set({ status: "anonymous", session: null });
        }

        throw error;
      }
    })();

    try {
      return await refreshInFlight;
    } finally {
      refreshInFlight = null;
    }
  },
  updateUser(user) {
    const currentSession = get().session;

    if (!currentSession) {
      return;
    }

    const nextSession = {
      ...currentSession,
      user,
    };

    saveAuthSession(nextSession);
    set({ session: nextSession });
  },
}));
