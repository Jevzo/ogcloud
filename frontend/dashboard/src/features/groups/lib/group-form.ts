import { groupFormSchema } from "@/features/groups/schemas";
import {
    coerceRuntimeSelection,
    supportsRuntimeProfile,
} from "@/features/groups/lib/group-runtime";
import type {
    CreateGroupPayload,
    GroupFormValues,
    GroupRecord,
    GroupType,
    UpdateGroupPayload,
} from "@/types/group";
import type { BackendRuntimeProfile } from "@/types/runtime";

const getPlayersPerServerByGroupType = (groupType: GroupType | string) => {
    const normalizedType = groupType.toUpperCase();

    if (normalizedType === "STATIC") {
        return "100";
    }

    if (normalizedType === "PROXY") {
        return "512";
    }

    return "50";
};

export const synchronizeGroupRuntimeFields = (values: GroupFormValues): GroupFormValues => {
    const nextRuntimeSelection = coerceRuntimeSelection(
        values.type,
        values.runtimeProfile,
        values.serverImage,
    );

    return {
        ...values,
        runtimeProfile: nextRuntimeSelection.runtimeProfile,
        serverImage: nextRuntimeSelection.serverImage,
    };
};

export const createEmptyGroupValues = (): GroupFormValues =>
    synchronizeGroupRuntimeFields({
        id: "",
        type: "DYNAMIC",
        templateBucket: "ogcloud-templates",
        templatePath: "ogcloud-templates",
        templateVersion: "",
        jvmFlags: "-Xms512M -Xmx512M",
        drainTimeoutSeconds: "30",
        serverImage: "",
        runtimeProfile: "MODERN_1_21_11",
        storageSize: "5Gi",
        scaling: {
            minOnline: "0",
            maxInstances: "1",
            playersPerServer: getPlayersPerServerByGroupType("DYNAMIC"),
            scaleUpThreshold: "0.8",
            scaleDownThreshold: "0.35",
            cooldownSeconds: "30",
        },
        resources: {
            memoryRequest: "512Mi",
            memoryLimit: "1Gi",
            cpuRequest: "250m",
            cpuLimit: "1000m",
        },
    });

export const applyGroupTypeToFormValues = (
    values: GroupFormValues,
    groupType: GroupType,
): GroupFormValues =>
    synchronizeGroupRuntimeFields({
        ...values,
        type: groupType,
        scaling: {
            ...values.scaling,
            playersPerServer: getPlayersPerServerByGroupType(groupType),
        },
    });

export const applyRuntimeProfileToFormValues = (
    values: GroupFormValues,
    runtimeProfile: GroupFormValues["runtimeProfile"],
): GroupFormValues =>
    synchronizeGroupRuntimeFields({
        ...values,
        runtimeProfile,
    });

export const toGroupFormValues = (group: GroupRecord): GroupFormValues =>
    synchronizeGroupRuntimeFields({
        id: group.id,
        type: group.type,
        templateBucket: group.templateBucket,
        templatePath: group.templatePath,
        templateVersion: group.templateVersion,
        jvmFlags: group.jvmFlags,
        drainTimeoutSeconds: `${group.drainTimeoutSeconds}`,
        serverImage: group.serverImage,
        runtimeProfile: group.runtimeProfile ?? "",
        storageSize: group.storageSize,
        scaling: {
            minOnline: `${group.scaling.minOnline}`,
            maxInstances: `${group.scaling.maxInstances}`,
            playersPerServer: `${group.scaling.playersPerServer}`,
            scaleUpThreshold: `${group.scaling.scaleUpThreshold}`,
            scaleDownThreshold: `${group.scaling.scaleDownThreshold}`,
            cooldownSeconds: `${group.scaling.cooldownSeconds}`,
        },
        resources: {
            memoryRequest: group.resources.memoryRequest,
            memoryLimit: group.resources.memoryLimit,
            cpuRequest: group.resources.cpuRequest,
            cpuLimit: group.resources.cpuLimit,
        },
    });

interface GroupConfigPayloadFields {
    templateBucket: string;
    templatePath: string;
    templateVersion: string;
    scaling: CreateGroupPayload["scaling"];
    resources: CreateGroupPayload["resources"];
    jvmFlags: string;
    drainTimeoutSeconds: number;
    serverImage: string;
    runtimeProfile?: CreateGroupPayload["runtimeProfile"];
    storageSize?: string;
}

const validateGroupFormValues = (values: GroupFormValues) => {
    const result = groupFormSchema.safeParse(values);

    if (!result.success) {
        throw new Error(result.error.issues[0]?.message ?? "Invalid group configuration.");
    }

    return result.data;
};

const buildGroupConfigPayloadFields = (values: GroupFormValues): GroupConfigPayloadFields => {
    const validatedValues = validateGroupFormValues(values);

    const numericFields = {
        drainTimeoutSeconds: Number.parseInt(validatedValues.drainTimeoutSeconds, 10),
        minOnline: Number.parseInt(validatedValues.scaling.minOnline, 10),
        maxInstances: Number.parseInt(validatedValues.scaling.maxInstances, 10),
        playersPerServer: Number.parseInt(validatedValues.scaling.playersPerServer, 10),
        scaleUpThreshold: Number.parseFloat(validatedValues.scaling.scaleUpThreshold),
        scaleDownThreshold: Number.parseFloat(validatedValues.scaling.scaleDownThreshold),
        cooldownSeconds: Number.parseInt(validatedValues.scaling.cooldownSeconds, 10),
    };

    for (const [key, numericValue] of Object.entries(numericFields)) {
        if (!Number.isFinite(numericValue)) {
            throw new Error(`Invalid value for ${key}.`);
        }
    }

    if (numericFields.minOnline > numericFields.maxInstances) {
        throw new Error("Min online cannot exceed max instances.");
    }

    const runtimeProfile: BackendRuntimeProfile | undefined = supportsRuntimeProfile(
        validatedValues.type,
    )
        ? validatedValues.runtimeProfile || undefined
        : undefined;

    return {
        templateBucket: validatedValues.templateBucket,
        templatePath: validatedValues.templatePath,
        templateVersion: validatedValues.templateVersion,
        jvmFlags: validatedValues.jvmFlags,
        drainTimeoutSeconds: numericFields.drainTimeoutSeconds,
        serverImage: validatedValues.serverImage,
        scaling: {
            minOnline: numericFields.minOnline,
            maxInstances: numericFields.maxInstances,
            playersPerServer: numericFields.playersPerServer,
            scaleUpThreshold: numericFields.scaleUpThreshold,
            scaleDownThreshold: numericFields.scaleDownThreshold,
            cooldownSeconds: numericFields.cooldownSeconds,
        },
        resources: {
            memoryRequest: validatedValues.resources.memoryRequest,
            memoryLimit: validatedValues.resources.memoryLimit,
            cpuRequest: validatedValues.resources.cpuRequest,
            cpuLimit: validatedValues.resources.cpuLimit,
        },
        ...(runtimeProfile ? { runtimeProfile } : {}),
        ...(validatedValues.type.toUpperCase() === "STATIC"
            ? { storageSize: validatedValues.storageSize }
            : {}),
    };
};

export const buildCreateGroupPayload = (values: GroupFormValues): CreateGroupPayload => {
    if (!values.id.trim()) {
        throw new Error("Group name is required.");
    }

    return {
        id: values.id.trim(),
        type: values.type,
        ...buildGroupConfigPayloadFields(values),
    };
};

export const buildUpdateGroupPayload = (values: GroupFormValues): UpdateGroupPayload =>
    buildGroupConfigPayloadFields(values);
