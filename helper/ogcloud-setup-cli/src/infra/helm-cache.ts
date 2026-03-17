import * as fs from "node:fs/promises";
import * as fsSync from "node:fs";
import * as https from "node:https";

import {
    CACHE_META_FILE,
    HELM_CACHE_DIR,
    HELM_CACHE_ROOT,
    HELM_REPO_URL,
    REMOTE_HELM_PATH,
    REPO_NAME,
    REPO_OWNER,
    REPO_REF,
} from "../shared/constants";
import { info, ok } from "../cli/output";
import { isObject, saveJsonFile } from "../shared/serialization";

function encodeRepoPath(repoPath: string): string {
    return repoPath
        .split("/")
        .filter(Boolean)
        .map((segment) => encodeURIComponent(segment))
        .join("/");
}

function httpsGetBuffer(
    url: string,
    headers: Record<string, string> = {},
    redirectCount = 0,
): Promise<Buffer> {
    return new Promise<Buffer>((resolve, reject) => {
        const request = https.get(
            url,
            {
                headers: {
                    "User-Agent": "ogcloud-setup-cli",
                    Accept: "application/vnd.github+json",
                    ...headers,
                },
            },
            (response) => {
                const statusCode = response.statusCode || 0;

                if ([301, 302, 307, 308].includes(statusCode) && response.headers.location) {
                    if (redirectCount >= 10) {
                        response.resume();
                        reject(new Error(`Too many redirects while requesting ${url}`));
                        return;
                    }

                    const redirectTarget = Array.isArray(response.headers.location)
                        ? response.headers.location[0]
                        : response.headers.location;
                    const redirectUrl = new URL(redirectTarget, url).toString();
                    response.resume();
                    resolve(httpsGetBuffer(redirectUrl, headers, redirectCount + 1));
                    return;
                }

                const chunks: Buffer[] = [];
                response.on("data", (chunk) => chunks.push(chunk));
                response.on("end", () => {
                    const body = Buffer.concat(chunks);
                    if (statusCode < 200 || statusCode >= 300) {
                        reject(
                            new Error(
                                `HTTP ${statusCode} for ${url}: ${body.toString("utf8").slice(0, 240)}`,
                            ),
                        );
                        return;
                    }
                    resolve(body);
                });
            },
        );

        request.on("error", (error) => reject(error));
    });
}

async function fetchJson<T>(url: string): Promise<T> {
    const buffer = await httpsGetBuffer(url);
    return JSON.parse(buffer.toString("utf8")) as T;
}

async function downloadGithubDirectory(repoPath: string, localPath: string): Promise<void> {
    await fs.mkdir(localPath, { recursive: true });
    const apiUrl = `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/contents/${encodeRepoPath(repoPath)}?ref=${REPO_REF}`;
    const entries = await fetchJson<unknown>(apiUrl);

    if (!Array.isArray(entries)) {
        throw new Error(`Unexpected GitHub API response while reading ${repoPath}.`);
    }

    for (const entryRaw of entries) {
        if (!isObject(entryRaw)) {
            throw new Error(`Unexpected directory entry while reading ${repoPath}.`);
        }

        const entryName = typeof entryRaw.name === "string" ? entryRaw.name : "";
        const entryType = typeof entryRaw.type === "string" ? entryRaw.type : "";
        if (!entryName || !entryType) {
            throw new Error(`Invalid directory entry while reading ${repoPath}.`);
        }

        const targetPath = `${localPath}/${entryName}`;
        if (entryType === "dir") {
            await downloadGithubDirectory(`${repoPath}/${entryName}`, targetPath);
            continue;
        }

        if (entryType === "file") {
            const downloadUrl =
                typeof entryRaw.download_url === "string" ? entryRaw.download_url : "";
            if (!downloadUrl) {
                throw new Error(`Missing download URL for ${repoPath}/${entryName}.`);
            }
            const content = await httpsGetBuffer(downloadUrl, {
                Accept: "application/octet-stream",
            });
            await fs.writeFile(targetPath, content);
        }
    }
}

function helmCacheIsReady(): boolean {
    const chartFiles = [
        `${HELM_CACHE_DIR}/ogcloud-infra/Chart.yaml`,
        `${HELM_CACHE_DIR}/ogcloud-platform/Chart.yaml`,
        `${HELM_CACHE_DIR}/ogcloud-dashboard/Chart.yaml`,
    ];
    return chartFiles.every((filePath) => fsSync.existsSync(filePath));
}

export async function ensureHelmCache(refresh: boolean): Promise<string> {
    if (!refresh && helmCacheIsReady()) {
        info(`Using cached Helm files: ${HELM_CACHE_DIR}`);
        return HELM_CACHE_DIR;
    }

    info(`Downloading Helm files from ${HELM_REPO_URL}`);
    await fs.rm(HELM_CACHE_ROOT, { recursive: true, force: true });
    await fs.mkdir(HELM_CACHE_ROOT, { recursive: true });
    await downloadGithubDirectory(REMOTE_HELM_PATH, HELM_CACHE_DIR);
    await saveJsonFile(CACHE_META_FILE, {
        source: HELM_REPO_URL,
        downloadedAt: new Date().toISOString(),
    });
    ok(`Helm files downloaded to ${HELM_CACHE_DIR}`);
    return HELM_CACHE_DIR;
}
