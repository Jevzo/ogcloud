/** What survives a reload: the rotating refresh token and the ID token that ends the API's login session. */
export interface StoredLogin {
  refreshToken: string;
  idToken: string | undefined;
}

export interface LoginStore {
  read(): Promise<StoredLogin | undefined>;
  write(login: StoredLogin | undefined): Promise<void>;
}

/** A sign-in in progress: kept per tab, read once on the callback. */
export interface PendingSignIn {
  verifier: string;
  state: string;
  returnTo: string;
}

export interface PendingStore {
  take(): PendingSignIn | undefined;
  put(pending: PendingSignIn): void;
}

const DATABASE = "ogcloud-auth";
const TABLE = "login";
const KEY = "current";

function request<T>(operation: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    operation.onsuccess = () => resolve(operation.result);
    operation.onerror = () =>
      reject(operation.error ?? new Error("IndexedDB failed without an error"));
  });
}

/** The login in IndexedDB, which every tab of the origin shares (D-0212). */
export function indexedDbLoginStore(
  factory: IDBFactory = indexedDB,
): LoginStore {
  const open = () => {
    const opening = factory.open(DATABASE, 1);
    opening.onupgradeneeded = () => opening.result.createObjectStore(TABLE);
    return request(opening);
  };
  const table = async (mode: IDBTransactionMode) =>
    (await open()).transaction(TABLE, mode).objectStore(TABLE);
  return {
    async read() {
      const value: unknown = await request((await table("readonly")).get(KEY));
      return isStoredLogin(value) ? value : undefined;
    },
    async write(login) {
      const store = await table("readwrite");
      if (login === undefined) {
        await request(store.delete(KEY));
      } else {
        await request(store.put(login, KEY));
      }
    },
  };
}

function isStoredLogin(value: unknown): value is StoredLogin {
  return (
    typeof value === "object" &&
    value !== null &&
    typeof (value as StoredLogin).refreshToken === "string"
  );
}

const PENDING_KEY = "ogcloud-auth-pending";

/** The pending sign-in in this tab's session storage. */
export function sessionPendingStore(
  storage: Storage = sessionStorage,
): PendingStore {
  return {
    take() {
      const raw = storage.getItem(PENDING_KEY);
      storage.removeItem(PENDING_KEY);
      if (raw === null) return undefined;
      const value: unknown = JSON.parse(raw);
      return typeof value === "object" && value !== null && "verifier" in value
        ? (value as PendingSignIn)
        : undefined;
    },
    put(pending) {
      storage.setItem(PENDING_KEY, JSON.stringify(pending));
    },
  };
}
