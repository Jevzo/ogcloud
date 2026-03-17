import type { ReactNode } from "react";
import { useEffect } from "react";
import { Navigate, useLocation } from "react-router";
import { LoaderCircleIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
            <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-6">
                <div className="pointer-events-none absolute inset-0" aria-hidden="true">
                    <div className="absolute inset-x-0 top-0 h-56 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.2),transparent_58%)]" />
                    <div className="absolute -left-20 top-16 h-72 w-72 rounded-full bg-blue-500/10 blur-3xl" />
                    <div className="absolute -right-16 bottom-12 h-72 w-72 rounded-full bg-sky-500/10 blur-3xl" />
                </div>
                <Card className="relative w-full max-w-sm border-border/70 bg-card/82 shadow-2xl shadow-black/15 backdrop-blur">
                    <CardHeader className="space-y-3">
                        <Badge variant="outline" className="w-fit border-primary/30 text-primary">
                            Session
                        </Badge>
                        <CardTitle>Restoring your session</CardTitle>
                        <CardDescription>
                            The dashboard is refreshing access credentials before loading protected
                            routes.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div className="flex items-center gap-2 text-sm text-muted-foreground">
                            <LoaderCircleIcon className="size-4 animate-spin text-primary" />
                            Reconnecting to the control plane
                        </div>
                    </CardContent>
                </Card>
            </div>
        );
    }

    return <>{children}</>;
};

export default RequireAuth;
