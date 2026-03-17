import { Suspense } from "react";
import { Outlet } from "react-router";

import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";

const AppShell = () => {
    return (
        <div className="dark min-h-screen bg-background text-foreground">
            <TooltipProvider delayDuration={150}>
                <Suspense fallback={null}>
                    <Outlet />
                </Suspense>
                <Toaster closeButton position="top-right" richColors />
            </TooltipProvider>
        </div>
    );
};

export default AppShell;
