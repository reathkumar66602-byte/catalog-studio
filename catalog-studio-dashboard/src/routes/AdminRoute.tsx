import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../store/auth";

export function AdminRoute() {
  const { user } = useAuth();
  if (user?.role !== "ADMIN") {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
