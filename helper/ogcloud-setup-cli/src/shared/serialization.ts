import * as fs from "node:fs/promises";
import * as path from "node:path";

import type { JsonMap } from "./types";

export async function readJsonFile<T>(filePath: string, fallbackValue: T): Promise<T> {
    try {
        const raw = await fs.readFile(filePath, "utf8");
        return JSON.parse(raw) as T;
    } catch (error) {
        const fsError = error as NodeJS.ErrnoException;
        if (fsError.code === "ENOENT") {
            return fallbackValue;
        }
        throw error;
    }
}

export async function saveJsonFile(filePath: string, value: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    await fs.writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function yamlScalar(value: unknown): string {
    if (value === null || value === undefined) {
        return "null";
    }
    if (typeof value === "boolean") {
        return value ? "true" : "false";
    }
    if (typeof value === "number") {
        return Number.isFinite(value) ? String(value) : "null";
    }

    const text = String(value);
    const lower = text.toLowerCase();
    const shouldQuote =
        text.length === 0 ||
        /^\d+$/.test(text) ||
        ["null", "true", "false", "~"].includes(lower) ||
        /[:#[\]{},&*!?|>'"%@`-]/.test(text) ||
        /\s/.test(text);
    return shouldQuote ? JSON.stringify(text) : text;
}

export function isObject(value: unknown): value is Record<string, unknown> {
    return value != null && typeof value === "object" && !Array.isArray(value);
}

function renderYamlLines(value: unknown, indent: number): string[] {
    const pad = "  ".repeat(indent);

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return [`${pad}[]`];
        }
        const lines: string[] = [];
        for (const item of value) {
            if (Array.isArray(item) || isObject(item)) {
                lines.push(`${pad}-`);
                lines.push(...renderYamlLines(item, indent + 1));
            } else {
                lines.push(`${pad}- ${yamlScalar(item)}`);
            }
        }
        return lines;
    }

    if (isObject(value)) {
        const keys = Object.keys(value);
        if (keys.length === 0) {
            return [`${pad}{}`];
        }

        const lines: string[] = [];
        for (const key of keys) {
            const item = value[key];
            if (Array.isArray(item) && item.length === 0) {
                lines.push(`${pad}${key}: []`);
            } else if (isObject(item) && Object.keys(item).length === 0) {
                lines.push(`${pad}${key}: {}`);
            } else if (Array.isArray(item) || isObject(item)) {
                lines.push(`${pad}${key}:`);
                lines.push(...renderYamlLines(item, indent + 1));
            } else {
                lines.push(`${pad}${key}: ${yamlScalar(item)}`);
            }
        }
        return lines;
    }

    return [`${pad}${yamlScalar(value)}`];
}

export function toYaml(value: unknown): string {
    return `${renderYamlLines(value, 0).join("\n")}\n`;
}

export function setDeepValue(target: JsonMap, pathParts: string[], value: unknown): void {
    let current: JsonMap = target;
    for (let i = 0; i < pathParts.length - 1; i += 1) {
        const key = pathParts[i];
        const nextValue = current[key];
        if (!isObject(nextValue)) {
            const nested: JsonMap = {};
            current[key] = nested;
            current = nested;
            continue;
        }
        current = nextValue;
    }
    current[pathParts[pathParts.length - 1]] = value;
}

export function getDeepValue(target: unknown, pathParts: string[]): unknown {
    let current = target;
    for (const key of pathParts) {
        if (!isObject(current)) {
            return undefined;
        }
        current = current[key];
    }
    return current;
}
