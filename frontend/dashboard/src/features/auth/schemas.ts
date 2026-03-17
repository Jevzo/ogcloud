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

export const loginFormSchema = z.object({
    email: z
        .string()
        .trim()
        .min(1, "Email is required.")
        .email("Enter a valid email address."),
    password: z.string().min(1, "Password is required."),
});

export const minecraftLinkRequestFormSchema = z.object({
    minecraftUsername: z.string().trim().min(1, "Enter your Minecraft username."),
});

export const minecraftLinkConfirmFormSchema = z.object({
    otp: z
        .string()
        .trim()
        .regex(/^\d{6}$/, "Enter the 6-digit code from Minecraft chat."),
});

export type AuthUserSchema = z.infer<typeof authUserSchema>;
export type AuthSessionSchema = z.infer<typeof authSessionSchema>;
export type LoginFormValues = z.infer<typeof loginFormSchema>;
export type MinecraftLinkRequestFormValues = z.infer<typeof minecraftLinkRequestFormSchema>;
export type MinecraftLinkConfirmFormValues = z.infer<typeof minecraftLinkConfirmFormSchema>;
