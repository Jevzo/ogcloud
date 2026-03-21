import { z } from "zod";

import { createPaginatedResponseSchema } from "@/api/shared";

export const auditMetadataSchema = z.record(z.string(), z.unknown());

export const apiAuditLogRecordSchema = z.object({
    id: z.string().min(1).nullable(),
    action: z.string().min(1),
    targetType: z.string().min(1),
    targetId: z.string().min(1),
    actorUserId: z.string().min(1).nullable(),
    actorEmail: z.string().min(1).nullable(),
    summary: z.string().min(1),
    metadata: auditMetadataSchema,
    timestamp: z.string().min(1),
});

export const apiAuditLogPageSchema = createPaginatedResponseSchema(apiAuditLogRecordSchema);

export type ApiAuditLogRecordSchema = z.infer<typeof apiAuditLogRecordSchema>;
