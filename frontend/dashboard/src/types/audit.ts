export type AuditMetadata = Record<string, unknown>;

export interface ApiAuditLogRecord {
    id: string | null;
    action: string;
    targetType: string;
    targetId: string;
    actorUserId: string | null;
    actorEmail: string | null;
    summary: string;
    metadata: AuditMetadata;
    timestamp: string;
}
