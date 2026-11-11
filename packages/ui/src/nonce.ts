import { createContext, useContext } from "react";

declare global {
  // The name webpack gave it; react-colorful and Radix's scroll lock (through get-nonce) read it whatever the bundler.
  var __webpack_nonce__: string | undefined;
}

// A nonce the server filled in: base64 or a UUID. In development the meta still holds the server's placeholder.
const NONCE = /^[A-Za-z0-9+/=_-]{16,}$/;

/**
 * The page's CSP nonce (D-0219), which its server writes into `<meta name="csp-nonce">` for every response; the
 * `<style>` elements the UI's libraries insert carry it. None in development, where no CSP applies.
 */
export function readCspNonce(doc: Document = document): string | undefined {
  const content = doc
    .querySelector('meta[name="csp-nonce"]')
    ?.getAttribute("content");
  return content != null && NONCE.test(content) ? content : undefined;
}

export const NonceContext = createContext<string | undefined>(undefined);

export function useNonce(): string | undefined {
  return useContext(NonceContext);
}
