import { mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, posix, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const distDir = join(root, process.env.CS_EXT_OUT || "dist");
const releaseDir = join(root, "release");
const pkg = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
const version = pkg.version;

const PROD_DASHBOARD = ["https://catalogstudio.in/*", "https://www.catalogstudio.in/*"];
const HOST_PERMISSIONS = [
  ...PROD_DASHBOARD,
  "https://supplier.meesho.com/*",
  "https://www.meesho.com/*",
  "https://meesho.com/*",
  "https://images.meesho.com/*",
];

function crc32(buffer) {
  let crc = ~0;
  for (let i = 0; i < buffer.length; i += 1) {
    crc ^= buffer[i];
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return ~crc >>> 0;
}

function collectFiles(dir, prefix = "") {
  const entries = [];
  for (const name of readdirSync(dir)) {
    if (name.endsWith(".map") || name === ".DS_Store") continue;
    const full = join(dir, name);
    const rel = prefix ? posix.join(prefix, name) : name;
    if (statSync(full).isDirectory()) {
      entries.push(...collectFiles(full, rel));
    } else {
      entries.push({ name: rel.replaceAll("\\", "/"), data: readFileSync(full) });
    }
  }
  return entries;
}

function zipStore(files) {
  const locals = [];
  const centrals = [];
  let offset = 0;
  for (const file of files) {
    const name = Buffer.from(file.name, "utf8");
    const crc = crc32(file.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 8);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(file.data.length, 18);
    local.writeUInt32LE(file.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    const localFull = Buffer.concat([local, name, file.data]);
    locals.push(localFull);

    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(file.data.length, 20);
    central.writeUInt32LE(file.data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    centrals.push(Buffer.concat([central, name]));
    offset += localFull.length;
  }
  const centralDir = Buffer.concat(centrals);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(files.length, 8);
  eocd.writeUInt16LE(files.length, 10);
  eocd.writeUInt32LE(centralDir.length, 12);
  eocd.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, centralDir, eocd]);
}

const manifestPath = join(distDir, "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
manifest.version = version;
manifest.host_permissions = HOST_PERMISSIONS;
manifest.homepage_url = "https://www.catalogstudio.in";
manifest.content_scripts = (manifest.content_scripts || []).map((script) => {
  if (Array.isArray(script.js) && script.js.includes("pair.js")) {
    return { ...script, matches: PROD_DASHBOARD };
  }
  return script;
});
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);

const required = ["manifest.json", "background.js", "content.js", "popup.html", "pair.js", "icons/icon128.png"];
for (const file of required) {
  const full = join(distDir, file);
  try {
    statSync(full);
  } catch {
    throw new Error(`Prod package is missing ${file}. Run generate-icons and the Vite build first.`);
  }
}

const background = readFileSync(join(distDir, "background.js"), "utf8");
if (!background.includes("www.catalogstudio.in")) {
  throw new Error("Prod background.js does not point at https://www.catalogstudio.in. Check .env.store and --mode store.");
}
if (JSON.stringify(manifest.host_permissions).includes("localhost")) {
  throw new Error("Prod manifest still allows localhost. Chrome Web Store review will flag this.");
}

mkdirSync(releaseDir, { recursive: true });
const zipName = `catalog-studio-extension-${version}.zip`;
const zipPath = join(releaseDir, zipName);
writeFileSync(zipPath, zipStore(collectFiles(distDir)));

console.log(`Chrome Web Store zip: ${relative(root, zipPath)}`);
console.log(`Prod unpacked build: ${relative(root, distDir)}`);
