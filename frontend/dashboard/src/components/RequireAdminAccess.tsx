import type { ReactNode } from "react";
import { Navigate } from "react-router";

import { hasAdminAccess } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";

interface RequireAdminAccessProps {
  children: ReactNode;
}

const RequireAdminAccess = ({ children }: RequireAdminAccessProps) => {
  const role = useAuthStore((state) => state.session?.user.role);

  if (!hasAdminAccess(role)) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default RequireAdminAccess;
