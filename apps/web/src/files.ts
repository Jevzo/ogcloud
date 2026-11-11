import { ApiError, type Problem } from "@ogcloud/api-client";
import type { ApiConnection } from "@ogcloud/api-client/react";

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
  const headers = await authorization(connection);
  return new Promise<T>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("POST", `${connection.origin ?? ""}${path}`);
    for (const [name, value] of Object.entries(headers)) {
      request.setRequestHeader(name, value);
    }
    request.setRequestHeader("Content-Type", contentType);
    request.upload.onprogress = (event) =>
      onProgress(event.loaded, event.total);
    request.onerror = () =>
      reject(new Error(`POST ${path} failed before an answer`));
    request.onload = () => {
      const text = request.responseText;
      if (request.status >= 200 && request.status < 300) {
        resolve(JSON.parse(text) as T);
        return;
      }
      let problem: Problem | undefined;
      try {
        problem = JSON.parse(text) as Problem;
      } catch {
        // Not a ProblemDetail; the error quotes the text instead.
        problem = undefined;
      }
      reject(new ApiError("POST", path, request.status, problem, text));
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
  const response = await fetch(`${connection.origin ?? ""}${path}`, {
    headers: await authorization(connection),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new ApiError("GET", path, response.status, undefined, text);
  }
  saveBlob(await response.blob(), filename);
}

async function authorization(
  connection: ApiConnection,
): Promise<Record<string, string>> {
  const token = await connection.accessToken();
  return token === undefined ? {} : { Authorization: `Bearer ${token}` };
}

/** Saves data as an indented JSON file, such as a privacy export (D-0107). */
export function saveJson(data: unknown, filename: string): void {
  saveBlob(
    new Blob([JSON.stringify(data, null, 2)], { type: "application/json" }),
    filename,
  );
}

function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
