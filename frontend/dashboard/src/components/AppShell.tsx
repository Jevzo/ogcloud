import {Suspense} from "react";
import {Outlet} from "react-router";

const AppShell = () => {
    return (
        <div className="min-h-screen bg-background-dark text-text-main">
            <Suspense fallback={null}>
                <Outlet/>
            </Suspense>
        </div>
    );
};

export default AppShell;
