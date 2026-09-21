import { defineConfig } from "vite";
import { env } from "node:process";

const outDir = env.CS_EXT_OUT || "dist";

export default defineConfig({
  publicDir: false,
  build: {
    emptyOutDir: false,
    outDir,
    lib: {
      entry: "src/background/service-worker.ts",
      name: "CatalogStudioBackground",
      formats: ["iife"],
      fileName: () => "background.js",
    },
  },
});
