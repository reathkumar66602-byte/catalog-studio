import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { labelDownloadPlugin } from "./vite.label-download";

export default defineConfig({
  plugins: [labelDownloadPlugin(), react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/swagger-ui": "http://localhost:8080",
      "/v3/api-docs": "http://localhost:8080",
    },
  },
  optimizeDeps: {
    include: ["pdfjs-dist"],
  },
  test: {
    environment: "jsdom",
  },
});
