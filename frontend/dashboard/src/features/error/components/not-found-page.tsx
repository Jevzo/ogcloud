import { Link, isRouteErrorResponse, useRouteError } from "react-router";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuthStore } from "@/store/auth-store";

const NotFound = () => {
    const routeError = useRouteError();
    const session = useAuthStore((state) => state.session);

    const status = isRouteErrorResponse(routeError) ? routeError.status : 404;
    const isMissingRoute = status === 404;
    const primaryHref = session ? "/" : "/login";
    const primaryLabel = session ? "Return to dashboard" : "Return to sign in";
    const routeMessage = isRouteErrorResponse(routeError)
        ? routeError.statusText || "The requested route could not be resolved."
        : routeError instanceof Error
          ? routeError.message
          : "The requested route could not be resolved.";

    const title = isMissingRoute
        ? "That route is not part of this dashboard."
        : "This route is currently unavailable.";
    const description = isMissingRoute
        ? "We could not match the requested URL to a valid dashboard route. The link may be stale, mistyped, or from another build."
        : routeMessage;
    const showTechnicalMessage = !isMissingRoute && routeMessage !== description;

    return (
        <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-background px-4 py-10">
            <div className="pointer-events-none absolute inset-0" aria-hidden="true">
                <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.18),transparent_42%),linear-gradient(180deg,rgba(15,23,42,0.96)_0%,rgba(2,6,23,1)_100%)]" />
                <div className="absolute left-1/2 top-[-12rem] h-[26rem] w-[26rem] -translate-x-1/2 rounded-full bg-blue-500/12 blur-3xl" />
                <div className="absolute bottom-[-10rem] left-[-6rem] h-[22rem] w-[22rem] rounded-full bg-sky-500/10 blur-3xl" />
                <div className="absolute bottom-[-12rem] right-[-4rem] h-[24rem] w-[24rem] rounded-full bg-cyan-400/8 blur-3xl" />
            </div>

            <section className="relative w-full max-w-lg">
                <Card className="border-border/70 bg-card/84 shadow-2xl shadow-black/20 backdrop-blur-xl">
                    <CardHeader className="space-y-5 text-center">
                        <img
                            src="/static/logo.webp"
                            alt="OgCloud"
                            className="mx-auto h-auto w-32 max-w-full object-contain"
                        />
                        <div className="space-y-3">
                            <p className="text-xs font-semibold uppercase tracking-[0.28em] text-primary">
                                Error {status}
                            </p>
                            <CardTitle className="text-5xl tracking-tight text-foreground sm:text-6xl">
                                {status}
                            </CardTitle>
                            <div className="space-y-2">
                                <p className="text-xl font-semibold text-foreground">{title}</p>
                                <CardDescription className="mx-auto max-w-md text-sm leading-6">
                                    {description}
                                </CardDescription>
                            </div>
                        </div>
                    </CardHeader>

                    <CardContent className="space-y-6">
                        {showTechnicalMessage ? (
                            <div className="rounded-2xl border border-border/70 bg-background/55 px-4 py-4 text-left">
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Router response
                                </div>
                                <div className="mt-2 text-sm leading-6 text-muted-foreground">
                                    {routeMessage}
                                </div>
                            </div>
                        ) : null}

                        <div className="h-px w-full bg-border/60" aria-hidden="true" />

                        <div className="flex flex-col gap-3 sm:flex-row">
                            <Button asChild size="lg" className="flex-1">
                                <Link to={primaryHref}>{primaryLabel}</Link>
                            </Button>
                            <Button
                                type="button"
                                size="lg"
                                variant="outline"
                                className="flex-1"
                                onClick={() => window.location.reload()}
                            >
                                Reload app
                            </Button>
                        </div>

                        <p className="text-center text-xs leading-5 text-muted-foreground">
                            If this route should exist, reload after the latest dashboard deploy
                            finishes.
                        </p>
                    </CardContent>
                </Card>
            </section>
        </div>
    );
};

export default NotFound;
