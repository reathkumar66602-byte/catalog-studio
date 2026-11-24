import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { clearAccessExpiry, rememberAccessExpiry, refreshAccessToken } from "../api/session";
import type { UserSummary } from "../types";

type AuthState = {
  user: UserSummary | null;
  setSession: (user: UserSummary, access: string, refresh: string, expiresInSeconds?: number) => void;
  updateUser: (user: UserSummary) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => {
    const raw = localStorage.getItem("cs_user");
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  });

  // Keep PWA sessions warm: refresh access JWT before expiry while the app is open.
  useEffect(() => {
    if (!localStorage.getItem("cs_refresh")) {
      return;
    }
    const tick = () => {
      void refreshAccessToken().then((access) => {
        if (!access && window.location.pathname !== "/login") {
          // Soft: wait for next API 401 to redirect; avoid bounce on public pages.
        }
        const raw = localStorage.getItem("cs_user");
        if (raw) {
          try {
            setUser(JSON.parse(raw) as UserSummary);
          } catch {
            /* ignore */
          }
        }
      });
    };
    tick();
    const id = window.setInterval(tick, 10 * 60 * 1000);
    return () => window.clearInterval(id);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      setSession: (next, access, refresh, expiresInSeconds = 900) => {
        localStorage.setItem("cs_access", access);
        localStorage.setItem("cs_refresh", refresh);
        localStorage.setItem("cs_user", JSON.stringify(next));
        rememberAccessExpiry(expiresInSeconds);
        setUser(next);
      },
      updateUser: (next) => {
        localStorage.setItem("cs_user", JSON.stringify(next));
        setUser(next);
      },
      logout: () => {
        localStorage.removeItem("cs_access");
        localStorage.removeItem("cs_refresh");
        localStorage.removeItem("cs_user");
        clearAccessExpiry();
        setUser(null);
      },
    }),
    [user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("AuthProvider missing");
  }
  return ctx;
}
