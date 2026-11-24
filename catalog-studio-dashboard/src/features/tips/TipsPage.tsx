import { Navigate } from "react-router-dom";

/** Legacy route — ecommerce tips now live under /tutorials. */
export function TipsPage() {
  return <Navigate to="/tutorials" replace />;
}
