import { Suspense } from "react";
import { LazyMotion, domAnimation } from "motion/react";
import { Outlet } from "react-router";

import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

const AppShell = () => {
    return (
        <div className="dark min-h-screen bg-background text-foreground">
            <LazyMotion features={domAnimation}>
                <TooltipProvider delayDuration={150}>
                    <Suspense fallback={null}>
                        <Outlet />
                    </Suspense>
                    <Toaster closeButton position="top-right" richColors />
                </TooltipProvider>
            </LazyMotion>
        </div>
    );
};

export default AppShell;
