import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { env } from "node:process";

const outDir = env.CS_EXT_OUT || "dist";

export default defineConfig({
  plugins: [react()],
  publicDir: "public",
  build: {
    outDir,
    emptyOutDir: true,
    rollupOptions: {
      input: {
        popup: "popup.html",
      },
      output: {
        entryFileNames: "[name].js",
        chunkFileNames: "chunks/[name].js",
        assetFileNames: "assets/[name][extname]",
      },
    },
  },
  test: {
    environment: "jsdom",
  },
});
