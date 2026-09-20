import { defineConfig } from "vite";

export default defineConfig({
  publicDir: false,
  esbuild: {
    minifyIdentifiers: false,
  },
  build: {
    emptyOutDir: false,
    outDir: "dist",
    minify: "esbuild",
    lib: {
      entry: "src/content/index.ts",
      name: "CatalogStudioContent",
      formats: ["iife"],
      fileName: () => "content.js",
    },
  },
});
