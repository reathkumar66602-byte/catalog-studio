import type { Plugin } from "vite";
import type { IncomingMessage, ServerResponse } from "node:http";

const pending = new Map<string, { filename: string; bytes: Buffer }>();

function safeName(raw: string) {
  const cleaned = raw.replace(/[^\w.\-]/g, "_");
  return cleaned.toLowerCase().endsWith(".pdf") ? cleaned : `${cleaned}.pdf`;
}

function handle(req: IncomingMessage, res: ServerResponse, next: () => void) {
  const path = (req.url ?? "").split("?")[0];
  if (req.method === "POST" && path === "/__cs_download") {
    const chunks: Buffer[] = [];
    req.on("data", (chunk) => chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)));
    req.on("end", () => {
      const id = crypto.randomUUID();
      pending.set(id, {
        filename: safeName(String(req.headers["x-filename"] || "download.pdf")),
        bytes: Buffer.concat(chunks),
      });
      setTimeout(() => pending.delete(id), 60_000);
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ url: `/__cs_download/${id}` }));
    });
    return;
  }
  const match = path.match(/^\/__cs_download\/([0-9a-f-]+)$/i);
  if (req.method === "GET" && match) {
    const item = pending.get(match[1]);
    if (!item) {
      res.statusCode = 404;
      res.end();
      return;
    }
    pending.delete(match[1]);
    res.setHeader("Content-Type", "application/octet-stream");
    res.setHeader("Content-Disposition", `attachment; filename="${item.filename}"`);
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("Content-Length", String(item.bytes.length));
    res.end(item.bytes);
    return;
  }
  next();
}

export function labelDownloadPlugin(): Plugin {
  return {
    name: "label-download",
    configureServer(server) {
      server.middlewares.stack.unshift({ route: "", handle });
    },
    configurePreviewServer(server) {
      server.middlewares.stack.unshift({ route: "", handle });
    },
  };
}
