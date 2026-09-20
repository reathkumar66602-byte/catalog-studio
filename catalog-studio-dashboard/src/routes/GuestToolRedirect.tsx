import { Navigate } from "react-router-dom";
import { useAuth } from "../store/auth";

export function GuestToolRedirect({ to }: { to: string }) {
  const { user } = useAuth();
  if (user) {
    return <Navigate to={to} replace />;
  }
  return <Navigate to={`/login?next=${encodeURIComponent(to)}`} replace />;
}
