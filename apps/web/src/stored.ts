/**
 * A convenience remembered on this device in `localStorage`. Blocked or corrupt storage only costs the remembered
 * value, so reads fall back to `null` and failed writes are logged.
 */
export function readStored(
  key: string,
  storage: Storage = localStorage,
): unknown {
  try {
    return JSON.parse(storage.getItem(key) ?? "null");
  } catch (error) {
    console.warn(`Could not read ${key}`, error);
    return null;
  }
}

export function writeStored(
  key: string,
  value: unknown,
  storage: Storage = localStorage,
): void {
  try {
    storage.setItem(key, JSON.stringify(value));
  } catch (error) {
    console.warn(`Could not keep ${key}`, error);
  }
}
