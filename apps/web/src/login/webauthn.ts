import type { LoginCsrf } from "@ogcloud/api-client";

// Spring Security's WebAuthn endpoints (D-0085) and the JSON they exchange, as its own login script sends it.
const AUTHENTICATE_OPTIONS = "/webauthn/authenticate/options";
const AUTHENTICATE = "/login/webauthn";
const REGISTER_OPTIONS = "/webauthn/register/options";
const REGISTER = "/webauthn/register";

function encode(buffer: ArrayBuffer): string {
  let binary = "";
  for (const byte of new Uint8Array(buffer))
    binary += String.fromCharCode(byte);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
}

function decode(value: string): ArrayBuffer {
  const binary = atob(value.replaceAll("-", "+").replaceAll("_", "/"));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++)
    bytes[index] = binary.charCodeAt(index);
  return bytes.buffer;
}

async function post<T>(
  path: string,
  csrf: LoginCsrf,
  body?: unknown,
): Promise<T> {
  const response = await fetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      [csrf.headerName]: csrf.token,
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (!response.ok) throw new Error(`${path} answered ${response.status}`);
  return (await response.json()) as T;
}

interface Described {
  id: string;
}

interface RequestOptions
  extends Omit<
    PublicKeyCredentialRequestOptions,
    "challenge" | "allowCredentials"
  > {
  challenge: string;
  allowCredentials?: (Omit<PublicKeyCredentialDescriptor, "id"> & Described)[];
}

interface CreationOptions
  extends Omit<
    PublicKeyCredentialCreationOptions,
    "challenge" | "user" | "excludeCredentials"
  > {
  challenge: string;
  user: Omit<PublicKeyCredentialUserEntity, "id"> & Described;
  excludeCredentials?: (Omit<PublicKeyCredentialDescriptor, "id"> &
    Described)[];
}

/** Signs in with a passkey and returns where the server continues the sign-in. */
export async function signInWithPasskey(csrf: LoginCsrf): Promise<string> {
  const options = await post<RequestOptions>(AUTHENTICATE_OPTIONS, csrf);
  const credential = (await navigator.credentials.get({
    publicKey: {
      ...options,
      challenge: decode(options.challenge),
      allowCredentials: (options.allowCredentials ?? []).map((allowed) => ({
        ...allowed,
        id: decode(allowed.id),
      })),
    },
  })) as PublicKeyCredential | null;
  if (credential === null) throw new Error("No passkey was chosen");
  const response = credential.response as AuthenticatorAssertionResponse;
  const result = await post<{ authenticated?: boolean; redirectUrl?: string }>(
    AUTHENTICATE,
    csrf,
    {
      id: credential.id,
      rawId: encode(credential.rawId),
      response: {
        authenticatorData: encode(response.authenticatorData),
        clientDataJSON: encode(response.clientDataJSON),
        signature: encode(response.signature),
        userHandle:
          response.userHandle === null
            ? undefined
            : encode(response.userHandle),
      },
      credType: credential.type,
      clientExtensionResults: credential.getClientExtensionResults(),
      authenticatorAttachment: credential.authenticatorAttachment,
    },
  );
  if (result.authenticated !== true || result.redirectUrl === undefined) {
    throw new Error("The server did not accept the passkey");
  }
  return result.redirectUrl;
}

/** Registers a new passkey for the signed-in user under a label they will recognise. */
export async function registerPasskey(
  csrf: LoginCsrf,
  label: string,
): Promise<void> {
  const options = await post<CreationOptions>(REGISTER_OPTIONS, csrf);
  const credential = (await navigator.credentials.create({
    publicKey: {
      ...options,
      challenge: decode(options.challenge),
      user: { ...options.user, id: decode(options.user.id) },
      excludeCredentials: (options.excludeCredentials ?? []).map(
        (excluded) => ({ ...excluded, id: decode(excluded.id) }),
      ),
    },
  })) as PublicKeyCredential | null;
  if (credential === null) throw new Error("No passkey was created");
  const response = credential.response as AuthenticatorAttestationResponse;
  const result = await post<{ success?: boolean }>(REGISTER, csrf, {
    publicKey: {
      label,
      credential: {
        id: credential.id,
        rawId: encode(credential.rawId),
        response: {
          attestationObject: encode(response.attestationObject),
          clientDataJSON: encode(response.clientDataJSON),
          transports: response.getTransports(),
        },
        type: credential.type,
        clientExtensionResults: credential.getClientExtensionResults(),
        authenticatorAttachment: credential.authenticatorAttachment,
      },
    },
  });
  if (result.success !== true)
    throw new Error("The server did not store the passkey");
}
