import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../store/auth";
import { hasFeature } from "./roles";

export function FeatureRoute({ feature }: { feature: string }) {
  const { user } = useAuth();
  if (!hasFeature(user, feature)) {
    return <Navigate to="/dashboard" replace />;
  }
  return <Outlet />;
}
