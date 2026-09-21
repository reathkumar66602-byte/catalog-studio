import { build, loadEnv } from "vite";

const modeIndex = process.argv.indexOf("--mode");
const mode = modeIndex >= 0 ? process.argv[modeIndex + 1] : "production";
const env = loadEnv(mode, process.cwd(), "VITE_");
const outDir = process.env.CS_EXT_OUT || "dist";

const entries = ["awake", "pair", "scout", "imgsync"];

for (const name of entries) {
  await build({
    configFile: false,
    mode,
    envDir: process.cwd(),
    publicDir: false,
    define: {
      "import.meta.env.VITE_API_BASE": JSON.stringify(env.VITE_API_BASE || ""),
      "import.meta.env.VITE_DASHBOARD_URL": JSON.stringify(env.VITE_DASHBOARD_URL || ""),
    },
    esbuild: { minifyIdentifiers: false },
    build: {
      emptyOutDir: false,
      outDir,
      minify: false,
      lib: {
        entry: `src/content/${name}.ts`,
        name: `CatalogStudio_${name}`,
        formats: ["iife"],
        fileName: () => `${name}.js`,
      },
    },
  });
}
