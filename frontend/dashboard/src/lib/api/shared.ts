import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { z, type ZodType } from "zod";

import { recordApiLatencySample } from "@/lib/api-latency";
import type { ApiErrorResponse } from "@/types/auth";
import { getPaginatedHasNext, type PaginatedResponse } from "@/types/dashboard";

type RuntimeConfigHost = {
    __OGCLOUD_CONFIG__?: {
        apiBaseUrl?: string;
    };
};

const DEFAULT_ERROR_MESSAGE = "Request failed.";
const DEFAULT_UNAUTHORIZED_MESSAGE = "Unauthorized request.";
const MAX_LIST_PAGE_COUNT = 1_000;
const MAX_LIST_PAGE_SIZE = 200;

export const DEFAULT_LOGIN_UNAUTHORIZED_MESSAGE = "Invalid email or password.";
export const SESSION_EXPIRED_MESSAGE = "Your session expired. Please sign in again.";

const getApiBaseUrl = () => {
    const runtimeConfig = (globalThis as RuntimeConfigHost).__OGCLOUD_CONFIG__;
    return (runtimeConfig?.apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL ?? "").replace(
        /\/+$/,
        "",
    );
};

const API_BASE_URL = getApiBaseUrl();

export const apiClient = axios.create({
    baseURL: API_BASE_URL || undefined,
    headers: {
        "Content-Type": "application/json",
    },
});

type TimedRequestConfig = InternalAxiosRequestConfig & {
    metadata?: {
        startedAtMs: number;
    };
};

const getNowMs = () =>
    typeof performance !== "undefined" && typeof performance.now === "function"
        ? performance.now()
        : Date.now();

const recordRequestLatency = (config?: TimedRequestConfig) => {
    const startedAtMs = config?.metadata?.startedAtMs;

    if (typeof startedAtMs !== "number" || !Number.isFinite(startedAtMs)) {
        return;
    }

    const latencyMs = Math.max(1, Math.round(getNowMs() - startedAtMs));
    recordApiLatencySample(latencyMs);
};

apiClient.interceptors.request.use((config) => {
    const nextConfig = config as TimedRequestConfig;
    nextConfig.metadata = {
        startedAtMs: getNowMs(),
    };
    return nextConfig;
});

apiClient.interceptors.response.use(
    (response) => {
        recordRequestLatency(response.config as TimedRequestConfig);
        return response;
    },
    (error) => {
        if (axios.isAxiosError(error)) {
            recordRequestLatency(error.config as TimedRequestConfig | undefined);
        }

        return Promise.reject(error);
    },
);

class ApiError extends Error {
    status: number;
    details: readonly string[];

    constructor(payload: Partial<ApiErrorResponse>, fallbackStatus: number) {
        super(payload.message || DEFAULT_ERROR_MESSAGE);
        this.name = "ApiError";
        this.status = payload.status ?? fallbackStatus;
        this.details = payload.details ?? [];
    }
}

export const toApiError = (error: unknown, unauthorizedMessage?: string) => {
    if (axios.isAxiosError(error)) {
        const axiosError = error as AxiosError<Partial<ApiErrorResponse>>;
        const status = axiosError.response?.status ?? 0;
        const payload = axiosError.response?.data;
        const fallbackMessage =
            status === 401
                ? unauthorizedMessage || DEFAULT_UNAUTHORIZED_MESSAGE
                : axiosError.message || DEFAULT_ERROR_MESSAGE;

        return new ApiError(
            {
                status,
                message: payload?.message || fallbackMessage,
                details: payload?.details ?? [],
            },
            status,
        );
    }

    if (error instanceof Error && error.message) {
        return new ApiError({ message: error.message, details: [] }, 0);
    }

    return new ApiError({ message: DEFAULT_ERROR_MESSAGE, details: [] }, 0);
};

export const getAuthHeaders = (accessToken: string) => ({
    Authorization: `Bearer ${accessToken}`,
});

export const parseWithSchema = <T>(
    schema: ZodType<T>,
    payload: unknown,
    invalidMessage: string,
) => {
    const result = schema.safeParse(payload);

    if (!result.success) {
        throw new Error(invalidMessage);
    }

    return result.data;
};

export const createPaginatedResponseSchema = <T>(itemSchema: ZodType<T>) =>
    z.object({
        items: z.array(itemSchema),
        page: z.number().int().nonnegative(),
        size: z.number().int().positive(),
        totalItems: z.number().int().nonnegative(),
    });

export const fetchAllPagedItems = async <T>(
    path: string,
    accessToken: string,
    params?: Record<string, number | string | undefined>,
    itemSchema?: ZodType<T>,
    invalidMessage?: string,
) => {
    const items: T[] = [];
    let page = 0;

    while (page < MAX_LIST_PAGE_COUNT) {
        const response = await apiClient.get<PaginatedResponse<T>>(path, {
            headers: getAuthHeaders(accessToken),
            params: {
                ...params,
                page,
                size: MAX_LIST_PAGE_SIZE,
            },
        });

        const paginatedResponse = itemSchema
            ? parseWithSchema(
                  createPaginatedResponseSchema(itemSchema),
                  response.data,
                  invalidMessage ?? `Received an invalid paginated response for ${path}.`,
              )
            : response.data;

        items.push(...paginatedResponse.items);

        if (!getPaginatedHasNext(paginatedResponse)) {
            return items;
        }

        page += 1;
    }

    throw new ApiError(
        {
            status: 500,
            message: `Pagination exceeded ${MAX_LIST_PAGE_COUNT} pages for ${path}.`,
            details: [],
        },
        500,
    );
};
