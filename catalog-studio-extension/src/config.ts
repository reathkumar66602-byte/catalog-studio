const apiBase = import.meta.env.VITE_API_BASE?.trim();
const dashboardUrl = import.meta.env.VITE_DASHBOARD_URL?.trim();

export const DEFAULT_API_BASE = apiBase || "http://localhost:8080/api/v1";
export const DEFAULT_DASHBOARD_URL = dashboardUrl || "http://localhost:5173";
export const SETUP_URL = `${DEFAULT_DASHBOARD_URL.replace(/\/$/, "")}/extension?ext_setup=1`;
