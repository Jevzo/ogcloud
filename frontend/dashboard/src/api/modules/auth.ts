import type { AuthSession, AuthUser, LoginCredentials } from "@/types/auth";
import { authSessionSchema, authUserSchema } from "@/features/auth/schemas";

import {
    apiClient,
    DEFAULT_LOGIN_UNAUTHORIZED_MESSAGE,
    getAuthHeaders,
    SESSION_EXPIRED_MESSAGE,
    toApiError,
} from "./shared";

export const loginWithEmailPassword = async (credentials: LoginCredentials) => {
    try {
        const response = await apiClient.post<AuthSession>("/api/v1/auth/login", credentials);
        return authSessionSchema.parse(response.data);
    } catch (error) {
        throw toApiError(error, DEFAULT_LOGIN_UNAUTHORIZED_MESSAGE);
    }
};

export const refreshSessionToken = async (refreshToken: string) => {
    try {
        const response = await apiClient.post<AuthSession>("/api/v1/auth/refresh", {
            refreshToken,
        });
        return authSessionSchema.parse(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const updateOwnProfile = async (
    accessToken: string,
    updates: { email?: string; password?: string },
) => {
    try {
        const response = await apiClient.put<AuthUser>("/api/v1/auth/me", updates, {
            headers: getAuthHeaders(accessToken),
        });
        return authUserSchema.parse(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const requestMinecraftLinkOtp = async (accessToken: string, minecraftUsername: string) => {
    try {
        await apiClient.post(
            "/api/v1/auth/link/request",
            { minecraftUsername },
            { headers: getAuthHeaders(accessToken) },
        );
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};

export const confirmMinecraftLinkOtp = async (accessToken: string, otp: string) => {
    try {
        const response = await apiClient.post<AuthUser>(
            "/api/v1/auth/link/confirm",
            { otp },
            { headers: getAuthHeaders(accessToken) },
        );
        return authUserSchema.parse(response.data);
    } catch (error) {
        throw toApiError(error, SESSION_EXPIRED_MESSAGE);
    }
};
