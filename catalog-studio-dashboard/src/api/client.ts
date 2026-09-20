import axios from "axios";
import type { ApiResponse, AuthPayload } from "../types";

const baseURL = import.meta.env.VITE_API_URL || "/api/v1";

export const api = axios.create({
  baseURL,
  timeout: 120000,
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem("cs_access");
  const url = String(config.url || "");
  const publicSite = url === "/site" || url.startsWith("/site/");
  if (token && !publicSite) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry && localStorage.getItem("cs_refresh")) {
      const url = String(original.url || "");
      if (url === "/site" || url.startsWith("/site/")) {
        return Promise.reject(error);
      }
      original._retry = true;
      try {
        const refresh = localStorage.getItem("cs_refresh");
        const { data } = await axios.post<ApiResponse<AuthPayload>>(`${baseURL}/auth/refresh-token`, {
          refreshToken: refresh,
        });
        localStorage.setItem("cs_access", data.data.accessToken);
        localStorage.setItem("cs_refresh", data.data.refreshToken);
        original.headers.Authorization = `Bearer ${data.data.accessToken}`;
        return api(original);
      } catch {
        localStorage.removeItem("cs_access");
        localStorage.removeItem("cs_refresh");
        window.location.href = "/login";
      }
    }
    if (error.response?.status === 402) {
      const here = window.location.pathname;
      if (here !== "/subscription" && !here.startsWith("/login")) {
        window.location.href = "/subscription";
      }
    }
    return Promise.reject(error);
  },
);

export function apiErrorMessage(error: unknown, fallback = "Something went wrong") {
  if (axios.isAxiosError(error)) {
    if (!error.response) {
      if (error.code === "ECONNABORTED") {
        return "The request timed out. Please try again.";
      }
      return "Could not reach Catalog Studio. Please try again in a moment.";
    }
    const data = error.response.data as { message?: string; errors?: { field?: string; message?: string }[] };
    if (data?.errors?.length) {
      return data.errors.map((item) => item.message).filter(Boolean).join(" ") || data.message || fallback;
    }
    return data?.message || fallback;
  }
  return fallback;
}
