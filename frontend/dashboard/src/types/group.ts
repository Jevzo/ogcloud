import type { BackendRuntimeProfile } from "@/types/runtime";

export const GROUP_TYPE_VALUES = ["DYNAMIC", "STATIC", "PROXY"] as const;

export type GroupType = (typeof GROUP_TYPE_VALUES)[number];

interface GroupScalingConfig {
    minOnline: number;
    maxInstances: number;
    playersPerServer: number;
    scaleUpThreshold: number;
    scaleDownThreshold: number;
    cooldownSeconds: number;
}

interface GroupResourceConfig {
    memoryRequest: string;
    memoryLimit: string;
    cpuRequest: string;
    cpuLimit: string;
}

interface GroupFormScalingValues {
    minOnline: string;
    maxInstances: string;
    playersPerServer: string;
    scaleUpThreshold: string;
    scaleDownThreshold: string;
    cooldownSeconds: string;
}

export interface GroupRecord {
    id: string;
    type: GroupType;
    templateBucket: string;
    templatePath: string;
    templateVersion: string;
    scaling: GroupScalingConfig;
    resources: GroupResourceConfig;
    jvmFlags: string;
    drainTimeoutSeconds: number;
    serverImage: string;
    runtimeProfile: BackendRuntimeProfile | null;
    storageSize: string;
    maintenance: boolean;
    createdAt: string;
    updatedAt: string;
}

export interface CreateGroupPayload {
    id: string;
    type: GroupType;
    templateBucket: string;
    templatePath: string;
    templateVersion: string;
    scaling: GroupScalingConfig;
    resources: GroupResourceConfig;
    jvmFlags: string;
    drainTimeoutSeconds: number;
    serverImage: string;
    runtimeProfile?: BackendRuntimeProfile;
    storageSize?: string;
}

export interface UpdateGroupPayload {
    templateBucket?: string;
    templatePath?: string;
    templateVersion?: string;
    scaling?: GroupScalingConfig;
    resources?: GroupResourceConfig;
    jvmFlags?: string;
    drainTimeoutSeconds?: number;
    serverImage?: string;
    runtimeProfile?: BackendRuntimeProfile;
    storageSize?: string;
}

export interface GroupFormValues {
    id: string;
    type: GroupType;
    templateBucket: string;
    templatePath: string;
    templateVersion: string;
    jvmFlags: string;
    drainTimeoutSeconds: string;
    serverImage: string;
    runtimeProfile: BackendRuntimeProfile | "";
    storageSize: string;
    scaling: GroupFormScalingValues;
    resources: GroupResourceConfig;
}
