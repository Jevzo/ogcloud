import { z } from "zod";

export const settingsEmailFormSchema = z.object({
    email: z
        .string()
        .trim()
        .min(1, "Email is required.")
        .email("Enter a valid email address."),
});

export const settingsPasswordFormSchema = z
    .object({
        password: z.string().min(1, "Password is required."),
        confirmPassword: z.string().min(1, "Confirm the new password."),
    })
    .refine((values) => values.password === values.confirmPassword, {
        message: "Passwords do not match.",
        path: ["confirmPassword"],
    });

export type SettingsEmailFormValues = z.infer<typeof settingsEmailFormSchema>;
export type SettingsPasswordFormValues = z.infer<typeof settingsPasswordFormSchema>;
