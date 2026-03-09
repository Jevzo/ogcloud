import type { ReactNode } from "react";
import { useEffect } from "react";
import { Navigate, useLocation } from "react-router";

import { useAuthStore } from "@/store/auth-store";

interface RequireAuthProps {
    children: ReactNode;
}

const RequireAuth = ({ children }: RequireAuthProps) => {
    const location = useLocation();
    const status = useAuthStore((state) => state.status);
    const session = useAuthStore((state) => state.session);
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const refreshKey = session ? `${session.refreshToken}:${session.accessTokenExpiresAt}` : null;

    useEffect(() => {
        if (!refreshKey) {
            return;
        }

        void refreshIfNeeded().catch(() => undefined);
    }, [refreshIfNeeded, refreshKey]);

    if (!session) {
        return (
            <Navigate
                to="/login"
                replace
                state={{ from: `${location.pathname}${location.search}${location.hash}` }}
            />
        );
    }

    if (status === "refreshing") {
        return (
            <div className="flex min-h-screen items-center justify-center bg-background-dark px-6">
                <div className="rounded-3xl border border-white/10 bg-white/5 px-6 py-5 text-sm text-text-muted shadow-[0_18px_60px_rgba(0,0,0,0.35)] backdrop-blur-2xl">
                    Restoring your session...
                </div>
            </div>
        );
    }

    return <>{children}</>;
};

export default RequireAuth;
