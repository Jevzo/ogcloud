import { z } from "zod";

export const permissionGroupDisplaySchema = z.object({
    chatPrefix: z.string(),
    chatSuffix: z.string(),
    nameColor: z.string(),
    tabPrefix: z.string(),
});

export const permissionGroupPermissionRecordSchema = z.object({
    perm: z.string().min(1),
    description: z.string(),
});

export const permissionGroupRecordSchema = z.object({
    id: z.string().min(1),
    name: z.string().min(1),
    display: permissionGroupDisplaySchema,
    weight: z.number().int(),
    default: z.boolean(),
    permissions: z.array(permissionGroupPermissionRecordSchema),
});

export const permissionGroupFormSchema = z
    .object({
        id: z.string().trim().min(1, "Group ID is required."),
        name: z.string().trim().min(1, "Display name is required."),
        weight: z.string().trim().min(1, "Weight is required."),
        default: z.boolean(),
        display: permissionGroupDisplaySchema,
    })
    .superRefine((values, context) => {
        const parsedWeight = Number.parseInt(values.weight, 10);

        if (!Number.isFinite(parsedWeight)) {
            context.addIssue({
                code: z.ZodIssueCode.custom,
                message: "Weight must be a valid number.",
                path: ["weight"],
            });
        }
    });

export const permissionNodeFormSchema = z.object({
    permission: z
        .string()
        .trim()
        .min(1, "Enter a permission node first.")
        .max(200, "Keep permission nodes under 200 characters."),
    description: z
        .string()
        .trim()
        .min(1, "Enter a description first.")
        .max(200, "Keep descriptions under 200 characters."),
});

export type PermissionGroupRecordSchema = z.infer<typeof permissionGroupRecordSchema>;
export type PermissionGroupFormSchema = z.infer<typeof permissionGroupFormSchema>;
export type PermissionNodeFormSchema = z.infer<typeof permissionNodeFormSchema>;
