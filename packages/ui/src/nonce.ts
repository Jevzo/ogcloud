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
  if (content != null && NONCE.test(content)) return content;
  if (content != null && import.meta.env.PROD) {
    // A built page whose server left the placeholder: the CSP refuses every style the libraries insert.
    console.error(`The CSP nonce was not filled in ("${content}")`);
  }
  return undefined;
}

export const NonceContext = createContext<string | undefined>(undefined);

export function useNonce(): string | undefined {
  return useContext(NonceContext);
}
