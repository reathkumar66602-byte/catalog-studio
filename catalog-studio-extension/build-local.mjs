import { spawnSync } from "node:child_process";
import { cpSync, mkdirSync, rmSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const out = process.env.CS_EXT_OUT || "dist-local";

function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: root,
    stdio: "inherit",
    shell: process.platform === "win32",
    env: { ...process.env, CS_EXT_OUT: out },
  });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

run("node", ["generate-icons.mjs"]);
run("npx", ["vite", "build"]);
run("npx", ["vite", "build", "--config", "vite.content.config.ts"]);
run("npx", ["vite", "build", "--config", "vite.background.config.ts"]);
run("node", ["build-extras.mjs"]);

if (out !== "dist") {
  const from = join(root, out);
  const distDir = join(root, "dist");
  rmSync(distDir, { recursive: true, force: true });
  mkdirSync(distDir, { recursive: true });
  cpSync(from, distDir, { recursive: true });
}

console.log(`Local unpacked: catalog-studio-extension/${out}`);
if (out !== "dist") {
  console.log("Also copied to catalog-studio-extension/dist for Chrome load unpacked.");
}
