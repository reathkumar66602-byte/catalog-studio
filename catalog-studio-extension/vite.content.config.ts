import { defineConfig } from "vite";
import { env } from "node:process";

const outDir = env.CS_EXT_OUT || "dist";

export default defineConfig({
  publicDir: false,
  esbuild: {
    minifyIdentifiers: false,
  },
  build: {
    emptyOutDir: false,
    outDir,
    minify: "esbuild",
    lib: {
      entry: "src/content/index.ts",
      name: "CatalogStudioContent",
      formats: ["iife"],
      fileName: () => "content.js",
    },
  },
});
