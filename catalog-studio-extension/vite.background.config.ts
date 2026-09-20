import { defineConfig } from "vite";

export default defineConfig({
  publicDir: false,
  build: {
    emptyOutDir: false,
    outDir: "dist",
    lib: {
      entry: "src/background/service-worker.ts",
      name: "CatalogStudioBackground",
      formats: ["iife"],
      fileName: () => "background.js",
    },
  },
});
