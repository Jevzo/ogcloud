import { templateRecordSchema } from "@/features/templates/schemas";
import type { PaginatedResponse } from "@/types/dashboard";
import type { TemplateRecord } from "@/types/template";

import {
    apiClient,
    createPaginatedResponseSchema,
    fetchAllPagedItems,
    getAuthHeaders,
    parseWithSchema,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

export const listAllTemplates = async (
    accessToken: string,
    params?: {
        group?: string;
        query?: string;
    },
) => {
    try {
        return await fetchAllPagedItems<TemplateRecord>(
            "/api/v1/templates",
            accessToken,
            params,
            templateRecordSchema,
            "Received an invalid template list response.",
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const listTemplates = async (
    accessToken: string,
    params?: {
        group?: string;
        query?: string;
        page?: number;
        size?: number;
    },
) => {
    try {
        const response = await apiClient.get<PaginatedResponse<TemplateRecord>>(
            "/api/v1/templates",
            {
                headers: getAuthHeaders(accessToken),
                params,
            },
        );

        return parseWithSchema(
            createPaginatedResponseSchema(templateRecordSchema),
            response.data,
            "Received an invalid paginated template response.",
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const uploadTemplate = async (
    accessToken: string,
    group: string,
    version: string,
    file: File,
) => {
    try {
        const formData = new FormData();
        formData.append("file", file);

        await apiClient.post(`/api/v1/templates/${encodeURIComponent(group)}/upload`, formData, {
            headers: {
                ...getAuthHeaders(accessToken),
                "Content-Type": "multipart/form-data",
            },
            params: {
                version,
            },
        });
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const downloadTemplate = async (accessToken: string, group: string, version: string) => {
    try {
        const response = await apiClient.get<BlobPart>(
            `/api/v1/templates/${encodeURIComponent(group)}/${encodeURIComponent(version)}/download`,
            {
                headers: getAuthHeaders(accessToken),
                responseType: "blob",
            },
        );

        const blob = new Blob([response.data], { type: "application/gzip" });
        const downloadUrl = window.URL.createObjectURL(blob);
        const fileName = `${group}-${version}-template.tar.gz`;
        const link = document.createElement("a");

        link.href = downloadUrl;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(downloadUrl);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const deleteTemplate = async (accessToken: string, group: string, version: string) => {
    try {
        await apiClient.delete(
            `/api/v1/templates/${encodeURIComponent(group)}/${encodeURIComponent(version)}`,
            {
                headers: getAuthHeaders(accessToken),
            },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
