import axios from "axios";
import type { ApiResponse, AuthPayload } from "../types";

const baseURL = import.meta.env.VITE_API_URL || "/api/v1";

const ACCESS_EXPIRES_AT_KEY = "cs_access_expires_at";

/** Persist access expiry so the PWA can refresh before the JWT dies. */
export function rememberAccessExpiry(expiresInSeconds: number) {
  const ms = Math.max(30, expiresInSeconds - 60) * 1000;
  localStorage.setItem(ACCESS_EXPIRES_AT_KEY, String(Date.now() + ms));
}

export function clearAccessExpiry() {
  localStorage.removeItem(ACCESS_EXPIRES_AT_KEY);
}

export function accessNeedsRefresh(): boolean {
  const raw = localStorage.getItem(ACCESS_EXPIRES_AT_KEY);
  if (!raw) {
    return Boolean(localStorage.getItem("cs_refresh") && localStorage.getItem("cs_access"));
  }
  return Date.now() >= Number(raw);
}

let refreshPromise: Promise<string | null> | null = null;

/** Single-flight refresh used by interceptors and proactive timers. */
export async function refreshAccessToken(): Promise<string | null> {
  if (refreshPromise) {
    return refreshPromise;
  }
  const refresh = localStorage.getItem("cs_refresh");
  if (!refresh) {
    return null;
  }
  refreshPromise = (async () => {
    try {
      const { data } = await axios.post<ApiResponse<AuthPayload>>(`${baseURL}/auth/refresh-token`, {
        refreshToken: refresh,
      });
      localStorage.setItem("cs_access", data.data.accessToken);
      localStorage.setItem("cs_refresh", data.data.refreshToken);
      rememberAccessExpiry(data.data.expiresInSeconds);
      if (data.data.user) {
        localStorage.setItem("cs_user", JSON.stringify(data.data.user));
      }
      return data.data.accessToken;
    } catch {
      localStorage.removeItem("cs_access");
      localStorage.removeItem("cs_refresh");
      localStorage.removeItem("cs_user");
      clearAccessExpiry();
      return null;
    } finally {
      refreshPromise = null;
    }
  })();
  return refreshPromise;
}
