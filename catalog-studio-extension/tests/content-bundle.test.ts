import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const bundle = resolve(__dirname, "../dist/content.js");

describe("content script bundle", () => {
  it.skipIf(!existsSync(bundle))("ships the seller UI markers used by the live content script", () => {
    const src = readFileSync(bundle, "utf8");
    // Static contract: avoid eval'ing the full production bundle in jsdom (MutationObserver / worker races).
    expect(src).toContain("cs-fab");
    expect(src).toContain("cs-sidebar");
    expect(src.length).toBeGreaterThan(10_000);
  });
});
