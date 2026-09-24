import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../store/auth";
import { isSuperAdmin } from "./roles";

export function SuperAdminRoute() {
  const { user } = useAuth();
  if (!isSuperAdmin(user?.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
