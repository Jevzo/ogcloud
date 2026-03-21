import { z } from "zod";

export const templateRecordSchema = z.object({
    group: z.string().min(1),
    version: z.string().min(1),
    path: z.string().min(1),
});

export const templateUploadFormSchema = z.object({
    group: z.string().trim().min(1, "Choose a target group first."),
    version: z.string().trim().min(1, "Enter a template version."),
    file: z.custom<File>((value) => value instanceof File, "Choose a template archive first."),
});

export type TemplateRecordSchema = z.infer<typeof templateRecordSchema>;
export type TemplateUploadFormValues = z.infer<typeof templateUploadFormSchema>;
