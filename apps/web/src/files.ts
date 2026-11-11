import { ApiError, problemIn } from "@ogcloud/api-client";
import { type ApiConnection, callOptions } from "@ogcloud/api-client/react";

/**
 * Sends a file as a request body and reports how much went out; fetch cannot report upload progress, so this uses
 * XMLHttpRequest. Answers outside 2xx throw an ApiError like every other call (D-0189).
 */
export async function uploadWithProgress<T>(
  connection: ApiConnection,
  path: string,
  file: Blob,
  contentType: string,
  onProgress: (sent: number, total: number) => void,
): Promise<T> {
  const call = await callOptions(connection);
  return new Promise<T>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", `${call.origin ?? ""}${path}`);
    if (call.token !== undefined)
      request.setRequestHeader("Authorization", `Bearer ${call.token}`);
    request.setRequestHeader("Content-Type", contentType);
    request.upload.onprogress = (event) =>
      onProgress(event.loaded, event.total);
    request.onerror = () =>
      reject(new Error(`POST ${path} failed before an answer`));
    request.onload = () => {
      const text = request.responseText;
      if (request.status < 200 || request.status >= 300) {
        reject(
          new ApiError("POST", path, request.status, problemIn(text), text),
        );
        return;
      }
      try {
        resolve(JSON.parse(text) as T);
      } catch (error) {
        reject(
          new Error(`POST ${path} answered ${request.status} without JSON`, {
            cause: error,
          }),
        );
      }
    };
    request.send(file);
  });
}

/** Fetches a file the API serves only to signed-in callers and hands it to the browser as a download. */
export async function downloadFile(
  connection: ApiConnection,
  path: string,
  filename: string,
): Promise<void> {
  const call = await callOptions(connection);
  const response = await fetch(`${call.origin ?? ""}${path}`, {
    headers:
      call.token === undefined ? {} : { Authorization: `Bearer ${call.token}` },
  });
  if (!response.ok) {
    const text = await response.text();
    throw new ApiError("GET", path, response.status, problemIn(text), text);
  }
  saveBlob(await response.blob(), filename);
}

/** Saves data as an indented JSON file, such as a privacy export (D-0107). */
export function saveJson(data: unknown, filename: string): void {
  saveBlob(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    filename,
  );
}

// Some browsers read the blob only after the click returns, so it stays valid for a while.
const REVOKE_AFTER_MS = 60_000;

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), REVOKE_AFTER_MS);
}
