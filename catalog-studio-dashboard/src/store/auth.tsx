import { createContext, useContext, useMemo, useState, type ReactNode } from "react";
import type { UserSummary } from "../types";

type AuthState = {
  user: UserSummary | null;
  setSession: (user: UserSummary, access: string, refresh: string) => void;
  updateUser: (user: UserSummary) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => {
    const raw = localStorage.getItem("cs_user");
    return raw ? (JSON.parse(raw) as UserSummary) : null;
  });

  const value = useMemo<AuthState>(
    () => ({
      user,
      setSession: (next, access, refresh) => {
        localStorage.setItem("cs_access", access);
        localStorage.setItem("cs_refresh", refresh);
        localStorage.setItem("cs_user", JSON.stringify(next));
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
