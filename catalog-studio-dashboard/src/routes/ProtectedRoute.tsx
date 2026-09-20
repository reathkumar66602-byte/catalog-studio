import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../store/auth";

export function ProtectedRoute() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const hasToken = Boolean(localStorage.getItem("cs_access") || localStorage.getItem("cs_refresh"));
  if (!user || !hasToken) {
    if (user && !hasToken) {
      logout();
    }
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  return <Outlet />;
}
