import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../store/auth";
import { isStaff } from "./roles";

export function AdminRoute() {
  const { user } = useAuth();
  if (!isStaff(user?.role)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
