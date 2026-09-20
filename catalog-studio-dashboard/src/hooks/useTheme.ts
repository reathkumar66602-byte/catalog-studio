import { useEffect, useState } from "react";

export function useTheme() {
  const [theme, setThemeState] = useState<"light" | "dark">(() =>
    localStorage.getItem("cs_theme") === "dark" ? "dark" : "light",
  );

  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
  }, [theme]);

  const setTheme = (next: "light" | "dark") => {
    localStorage.setItem("cs_theme", next);
    setThemeState(next);
  };

  return { theme, setTheme };
}
