import { z } from "zod";

export const authUserSchema = z.object({
    id: z.string().min(1),
    email: z.string().min(1),
    username: z.string().min(1),
    role: z.string().min(1),
    linkedPlayerUuid: z.string().min(1).nullable(),
});

export const authSessionSchema = z.object({
    accessToken: z.string().min(1),
    accessTokenExpiresAt: z.string().min(1),
    refreshToken: z.string().min(1),
    refreshTokenExpiresAt: z.string().min(1),
    user: authUserSchema,
});

export type AuthUserSchema = z.infer<typeof authUserSchema>;
export type AuthSessionSchema = z.infer<typeof authSessionSchema>;
