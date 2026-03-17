import { z } from "zod";

import { WEB_USER_ROLES } from "@/types/web-user";

export const webUserRoleSchema = z.enum(WEB_USER_ROLES);

export const webUserRecordSchema = z.object({
    id: z.string().min(1),
    email: z.string().email(),
    username: z.string().min(1),
    role: webUserRoleSchema,
    linkedPlayerUuid: z.string().min(1).nullable(),
});

export const createWebUserFormSchema = z.object({
    email: z.string().trim().min(1, "Email is required.").email("Enter a valid email address."),
    password: z.string().trim().min(1, "Password is required."),
    role: webUserRoleSchema,
});

export const updateWebUserFormSchema = z.object({
    email: z.string().trim().min(1, "Email is required.").email("Enter a valid email address."),
    username: z.string().trim().min(1, "Username is required."),
    password: z.string(),
    role: webUserRoleSchema,
});

export type WebUserRecordSchema = z.infer<typeof webUserRecordSchema>;
export type CreateWebUserFormValues = z.infer<typeof createWebUserFormSchema>;
export type UpdateWebUserFormValues = z.infer<typeof updateWebUserFormSchema>;
