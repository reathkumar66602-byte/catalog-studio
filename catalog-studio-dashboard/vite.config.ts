import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { VitePWA } from "vite-plugin-pwa";
import { labelDownloadPlugin } from "./vite.label-download";

export default defineConfig({
  plugins: [
    labelDownloadPlugin(),
    react(),
    tailwindcss(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg", "apple-touch-icon.png", "logo.png", "logo.svg"],
      manifest: {
        id: "/",
        name: "Catalog Studio",
        short_name: "Catalog Studio",
        description: "AI marketplace listings, labels, and tools for Meesho and Flipkart sellers",
        theme_color: "#0F766E",
        background_color: "#0F766E",
        display: "standalone",
        orientation: "any",
        start_url: "/",
        scope: "/",
        lang: "en",
        categories: ["business", "productivity"],
        icons: [
          {
            src: "pwa-192.png",
            sizes: "192x192",
            type: "image/png",
          },
          {
            src: "pwa-512.png",
            sizes: "512x512",
            type: "image/png",
          },
          {
            src: "pwa-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        navigateFallback: "/index.html",
        globPatterns: ["**/*.{js,css,html,ico,png,svg,jpg,jpeg,webp,woff2}"],
        runtimeCaching: [
          {
            urlPattern: ({ url }) => url.pathname.startsWith("/api/"),
            handler: "NetworkOnly",
          },
        ],
      },
      devOptions: {
        enabled: false,
      },
    }),
  ],
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
