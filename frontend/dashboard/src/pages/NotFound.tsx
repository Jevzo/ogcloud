import { Link, isRouteErrorResponse, useNavigate, useRouteError } from "react-router";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuthStore } from "@/store/auth-store";

const NotFound = () => {
    const navigate = useNavigate();
    const routeError = useRouteError();
    const session = useAuthStore((state) => state.session);

    const status = isRouteErrorResponse(routeError) ? routeError.status : 404;
    const title = status === 404 ? "Page not found" : "Route unavailable";
    const description = isRouteErrorResponse(routeError)
        ? routeError.statusText || "The requested route could not be resolved."
        : routeError instanceof Error
          ? routeError.message
          : "The requested route could not be resolved.";
    const primaryHref = session ? "/" : "/login";
    const primaryLabel = session ? "Back to dashboard" : "Back to sign in";

    return (
        <div className="relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-10">
            <div className="pointer-events-none absolute inset-0" aria-hidden="true">
                <div className="absolute inset-x-0 top-0 h-64 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.22),transparent_58%)]" />
                <div className="absolute -left-20 top-12 h-72 w-72 rounded-full bg-blue-500/10 blur-3xl" />
                <div className="absolute -right-20 bottom-6 h-72 w-72 rounded-full bg-sky-500/10 blur-3xl" />
            </div>

            <Card className="relative w-full max-w-xl border-border/70 bg-card/82 shadow-2xl shadow-black/15 backdrop-blur">
                <CardHeader className="space-y-3">
                    <Badge variant="outline" className="w-fit border-primary/30 text-primary">
                        Error {status}
                    </Badge>
                    <CardTitle className="text-3xl tracking-tight">{title}</CardTitle>
                    <CardDescription className="text-sm leading-6">
                        {description}
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-4 text-sm text-muted-foreground">
                    <div className="rounded-2xl border border-border/70 bg-background/55 px-4 py-3">
                        The destination may have moved, the link may be stale, or the route may no
                        longer exist in the current dashboard build.
                    </div>
                </CardContent>
                <CardFooter className="flex-col gap-2 sm:flex-row sm:justify-between">
                    <Button asChild>
                        <Link to={primaryHref}>{primaryLabel}</Link>
                    </Button>
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => navigate(-1)}
                    >
                        Go back
                    </Button>
                </CardFooter>
            </Card>
        </div>
    );
};

export default NotFound;
