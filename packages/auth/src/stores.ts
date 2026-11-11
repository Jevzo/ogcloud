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

function opened(opening: IDBOpenDBRequest): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    opening.onsuccess = () => resolve(opening.result);
    opening.onerror = () =>
      reject(opening.error ?? new Error("IndexedDB did not open"));
  });
}

/** Runs one request in its own transaction and settles once that transaction committed or failed. */
async function inTransaction<T>(
  database: Promise<IDBDatabase>,
  mode: IDBTransactionMode,
  work: (store: IDBObjectStore) => IDBRequest<T>,
): Promise<T> {
  const connection = await database;
  return new Promise((resolve, reject) => {
    const transaction = connection.transaction(TABLE, mode);
    const operation = work(transaction.objectStore(TABLE));
    const fail = () => {
      connection.close();
      reject(
        transaction.error ??
          operation.error ??
          new Error("IndexedDB failed without an error"),
      );
    };
    transaction.oncomplete = () => {
      connection.close();
      resolve(operation.result);
    };
    transaction.onerror = fail;
    transaction.onabort = fail;
  });
}

/** The login in IndexedDB, which every tab of the origin shares (D-0212). */
export function indexedDbLoginStore(
  factory: IDBFactory = indexedDB,
): LoginStore {
  const open = () => {
    const opening = factory.open(DATABASE, 1);
    opening.onupgradeneeded = () => opening.result.createObjectStore(TABLE);
    return opened(opening);
  };
  return {
    async read() {
      const value: unknown = await inTransaction(open(), "readonly", (store) =>
        store.get(KEY),
      );
      return isStoredLogin(value) ? value : undefined;
    },
    async write(login) {
      if (login === undefined) {
        await inTransaction(open(), "readwrite", (store) => store.delete(KEY));
      } else {
        await inTransaction(open(), "readwrite", (store) =>
          store.put(login, KEY),
        );
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
