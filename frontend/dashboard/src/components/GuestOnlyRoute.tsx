import type { ReactNode } from "react";
import { Navigate } from "react-router";

import { useAuthStore } from "@/store/auth-store";

interface GuestOnlyRouteProps {
  children: ReactNode;
}

const GuestOnlyRoute = ({ children }: GuestOnlyRouteProps) => {
  const session = useAuthStore((state) => state.session);

  if (session) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default GuestOnlyRoute;
