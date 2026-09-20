export function isExtensionAlive() {
  try {
    return typeof chrome !== "undefined" && Boolean(chrome.runtime?.id);
  } catch {
    return false;
  }
}

export function isInvalidatedContext(error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  return /extension context invalidated|message port closed|receiving end does not exist/i.test(message);
}

export async function ignoreInvalidated<T>(work: () => Promise<T>, fallback: T): Promise<T> {
  if (!isExtensionAlive()) return fallback;
  try {
    return await work();
  } catch (error) {
    if (isInvalidatedContext(error)) return fallback;
    throw error;
  }
}

export async function storageGet(keys: string[]): Promise<Record<string, any>> {
  return ignoreInvalidated(() => chrome.storage.local.get(keys), {});
}

export async function storageSet(items: Record<string, unknown>) {
  await ignoreInvalidated(async () => {
    await chrome.storage.local.set(items);
  }, undefined);
}

export async function sendRuntimeMessage<T = unknown>(message: unknown): Promise<T | null> {
  return ignoreInvalidated(async () => (await chrome.runtime.sendMessage(message)) as T, null);
}

export function watchStorageChanges(listener: (changes: { [key: string]: chrome.storage.StorageChange }, area: string) => void) {
  if (!isExtensionAlive()) return;
  try {
    chrome.storage.onChanged.addListener((changes, area) => {
      if (!isExtensionAlive()) return;
      listener(changes, area);
    });
  } catch (error) {
    if (!isInvalidatedContext(error)) throw error;
  }
}
