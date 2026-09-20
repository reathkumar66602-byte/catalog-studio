import { deflateSync } from "node:zlib";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const outDir = join(root, "public", "icons");

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

function pngChunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const tag = Buffer.from(type);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(Buffer.concat([tag, data])));
  return Buffer.concat([length, tag, data, crc]);
}

function encodePng(rgba, size) {
  const raw = Buffer.alloc(size * (1 + size * 4));
  for (let y = 0; y < size; y += 1) {
    const row = y * (1 + size * 4);
    raw[row] = 0;
    rgba.copy(raw, row + 1, y * size * 4, (y + 1) * size * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8;
  ihdr[9] = 6;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    pngChunk("IHDR", ihdr),
    pngChunk("IDAT", deflateSync(raw, { level: 9 })),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}

function mix(a, b, t) {
  return Math.round(a + (b - a) * t);
}

function inRoundRect(x, y, x0, y0, w, h, radius) {
  const x1 = x0 + w;
  const y1 = y0 + h;
  if (x < x0 || y < y0 || x >= x1 || y >= y1) return false;
  const r = Math.max(0, radius);
  const cx = x < x0 + r ? x0 + r : x >= x1 - r ? x1 - r - 1 : x;
  const cy = y < y0 + r ? y0 + r : y >= y1 - r ? y1 - r - 1 : y;
  if (cx === x && cy === y) return true;
  const dx = x - cx;
  const dy = y - cy;
  return dx * dx + dy * dy <= r * r;
}

function fillRoundRect(rgba, size, x0, y0, w, h, radius, color) {
  const xStart = Math.max(0, Math.floor(x0));
  const yStart = Math.max(0, Math.floor(y0));
  const xEnd = Math.min(size, Math.ceil(x0 + w));
  const yEnd = Math.min(size, Math.ceil(y0 + h));
  for (let y = yStart; y < yEnd; y += 1) {
    for (let x = xStart; x < xEnd; x += 1) {
      if (!inRoundRect(x, y, x0, y0, w, h, radius)) continue;
      const i = (y * size + x) * 4;
      const alpha = color[3] / 255;
      rgba[i] = mix(rgba[i], color[0], alpha);
      rgba[i + 1] = mix(rgba[i + 1], color[1], alpha);
      rgba[i + 2] = mix(rgba[i + 2], color[2], alpha);
      rgba[i + 3] = 255;
    }
  }
}

function drawStar(rgba, size, cx, cy, outer, inner, color) {
  const points = [];
  for (let i = 0; i < 10; i += 1) {
    const radius = i % 2 === 0 ? outer : inner;
    const angle = -Math.PI / 2 + (i * Math.PI) / 5;
    points.push([cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius]);
  }
  const minX = Math.max(0, Math.floor(cx - outer));
  const maxX = Math.min(size, Math.ceil(cx + outer));
  const minY = Math.max(0, Math.floor(cy - outer));
  const maxY = Math.min(size, Math.ceil(cy + outer));
  for (let y = minY; y < maxY; y += 1) {
    for (let x = minX; x < maxX; x += 1) {
      let inside = false;
      for (let i = 0, j = points.length - 1; i < points.length; j = i, i += 1) {
        const [xi, yi] = points[i];
        const [xj, yj] = points[j];
        const intersect = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi + 0.0001) + xi;
        if (intersect) inside = !inside;
      }
      if (!inside) continue;
      const i = (y * size + x) * 4;
      rgba[i] = color[0];
      rgba[i + 1] = color[1];
      rgba[i + 2] = color[2];
      rgba[i + 3] = 255;
    }
  }
}

function renderIcon(size) {
  const rgba = Buffer.alloc(size * size * 4, 0);
  const s = size / 64;
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      if (!inRoundRect(x, y, 0, 0, size, size, 16 * s)) continue;
      const t = (x + y) / (size * 2);
      const i = (y * size + x) * 4;
      rgba[i] = mix(15, 19, t);
      rgba[i + 1] = mix(118, 78, t);
      rgba[i + 2] = mix(110, 74, t);
      rgba[i + 3] = 255;
    }
  }
  fillRoundRect(rgba, size, 20 * s, 13 * s, 26 * s, 34 * s, 5 * s, [94, 234, 212, 97]);
  fillRoundRect(rgba, size, 17 * s, 16 * s, 26 * s, 34 * s, 5 * s, [153, 246, 228, 178]);
  fillRoundRect(rgba, size, 13 * s, 20 * s, 28 * s, 30 * s, 6 * s, [255, 255, 255, 255]);
  fillRoundRect(rgba, size, 18 * s, 26 * s, 11 * s, 3.2 * s, 1.6 * s, [15, 118, 110, 255]);
  fillRoundRect(rgba, size, 18 * s, 32.5 * s, 18 * s, 2.4 * s, 1.2 * s, [94, 234, 212, 255]);
  fillRoundRect(rgba, size, 18 * s, 38 * s, 14 * s, 2.4 * s, 1.2 * s, [153, 246, 228, 255]);
  drawStar(rgba, size, 49 * s, 20 * s, 6 * s, 2.6 * s, [253, 230, 138]);
  return encodePng(rgba, size);
}

mkdirSync(outDir, { recursive: true });
for (const size of [16, 32, 48, 128]) {
  writeFileSync(join(outDir, `icon${size}.png`), renderIcon(size));
}
console.log(`Wrote Chrome icons to ${outDir}`);
