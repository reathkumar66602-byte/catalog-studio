import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

const bundle = resolve(__dirname, "../dist/content.js");

describe("content script bundle", () => {
  it.skipIf(!existsSync(bundle))("starts on a catalog page without throwing", async () => {
    const chromeMock = {
      storage: {
        local: {
          get: async () => ({}),
          set: async () => undefined,
        },
        onChanged: { addListener() {} },
      },
      runtime: {
        id: "test-extension",
        sendMessage: async () => ({}),
        connect: () => ({ onMessage: { addListener() {} }, disconnect() {} }),
        onMessage: { addListener() {} },
        lastError: undefined,
      },
    };
    Object.assign(window, { chrome: chromeMock });
    Object.assign(globalThis, { chrome: chromeMock });
    expect(() => window.eval(readFileSync(bundle, "utf8"))).not.toThrow();
    await new Promise((resolveWait) => setTimeout(resolveWait, 30));
    expect(document.getElementById("cs-fab")).toBeTruthy();
    expect(document.getElementById("cs-sidebar")).toBeTruthy();
  });
});
