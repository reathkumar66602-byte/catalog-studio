import { createContext, useContext, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "../../api/client";
import { FALLBACK_SITE, type SitePublic } from "./types";

const SiteContext = createContext<SitePublic>(FALLBACK_SITE);

export function SiteProvider({ children }: { children: ReactNode }) {
  const { data } = useQuery({
    queryKey: ["public-site"],
    queryFn: async () => (await api.get("/site")).data.data as SitePublic,
    staleTime: 60_000,
    retry: 1,
  });
  return <SiteContext.Provider value={data ?? FALLBACK_SITE}>{children}</SiteContext.Provider>;
}

export function useSite() {
  return useContext(SiteContext);
}
