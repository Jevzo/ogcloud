import { createRoot } from "react-dom/client";
import { Suspense } from "react";
import { createBrowserRouter, RouterProvider } from "react-router";

import "@/index.css";

import AppShell from "@/components/AppShell";
import LandingPage from "@/pages/LandingPage";
import NotFound from "@/pages/NotFound";

const router = createBrowserRouter([
    {
        element: <AppShell />,
        errorElement: (
            <Suspense fallback={null}>
                <NotFound />
            </Suspense>
        ),
        children: [{ path: "/", element: <LandingPage /> }],
    },
]);

createRoot(document.getElementById("root")!).render(<RouterProvider router={router} />);
