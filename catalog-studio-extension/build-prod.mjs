import { spawnSync } from "node:child_process";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const mode = "store";
const out = process.env.CS_EXT_OUT || "dist-prod";

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
run("npx", ["vite", "build", "--mode", mode]);
run("npx", ["vite", "build", "--config", "vite.content.config.ts", "--mode", mode]);
run("npx", ["vite", "build", "--config", "vite.background.config.ts", "--mode", mode]);
run("node", ["build-extras.mjs", "--mode", mode]);
run("node", ["package-prod.mjs"]);
