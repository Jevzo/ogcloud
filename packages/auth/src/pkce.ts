const VERIFIER_BYTES = 32;
const STATE_BYTES = 16;

function base64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
}

function random(crypto: Crypto, length: number): string {
  return base64url(crypto.getRandomValues(new Uint8Array(length)));
}

/** A PKCE pair (RFC 7636, S256) and the request's state, all from the platform's CSPRNG. */
export async function createChallenge(
  crypto: Crypto,
): Promise<{ verifier: string; challenge: string; state: string }> {
  const verifier = random(crypto, VERIFIER_BYTES);
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(verifier),
  );
  return {
    verifier,
    challenge: base64url(new Uint8Array(digest)),
    state: random(crypto, STATE_BYTES),
  };
}
