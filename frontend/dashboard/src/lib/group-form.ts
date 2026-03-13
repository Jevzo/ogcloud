import {
    coerceRuntimeSelection,
    isServerImageCompatible,
    supportsRuntimeProfile,
} from "@/lib/group-runtime";
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

const buildGroupConfigPayloadFields = (values: GroupFormValues): GroupConfigPayloadFields => {
    const requiredFields = [
        ["Template bucket", values.templateBucket],
        ["Template", values.templatePath],
        ["Template version", values.templateVersion],
        ["JVM flags", values.jvmFlags],
        ["Server image", values.serverImage],
        ["Memory request", values.resources.memoryRequest],
        ["Memory limit", values.resources.memoryLimit],
        ["CPU request", values.resources.cpuRequest],
        ["CPU limit", values.resources.cpuLimit],
    ] as const;

    for (const [label, fieldValue] of requiredFields) {
        if (!fieldValue.trim()) {
            throw new Error(`${label} is required.`);
        }
    }

    if (values.type.toUpperCase() === "STATIC" && !values.storageSize.trim()) {
        throw new Error("Storage size is required for STATIC groups.");
    }

    if (supportsRuntimeProfile(values.type) && !values.runtimeProfile) {
        throw new Error("Runtime profile is required for backend groups.");
    }

    if (!isServerImageCompatible(values.type, values.runtimeProfile, values.serverImage.trim())) {
        if (values.type.toUpperCase() === "PROXY") {
            throw new Error("Proxy groups must use a velocity image.");
        }

        if (values.runtimeProfile === "LEGACY_1_8_8") {
            throw new Error("Legacy 1.8.8 groups must use ogwarsdev/paper:1.8.8.");
        }

        throw new Error("Modern backend groups must use a paper image tagged 1.21.11.");
    }

    const numericFields = {
        drainTimeoutSeconds: Number.parseInt(values.drainTimeoutSeconds, 10),
        minOnline: Number.parseInt(values.scaling.minOnline, 10),
        maxInstances: Number.parseInt(values.scaling.maxInstances, 10),
        playersPerServer: Number.parseInt(values.scaling.playersPerServer, 10),
        scaleUpThreshold: Number.parseFloat(values.scaling.scaleUpThreshold),
        scaleDownThreshold: Number.parseFloat(values.scaling.scaleDownThreshold),
        cooldownSeconds: Number.parseInt(values.scaling.cooldownSeconds, 10),
    };

    for (const [key, numericValue] of Object.entries(numericFields)) {
        if (!Number.isFinite(numericValue)) {
            throw new Error(`Invalid value for ${key}.`);
        }
    }

    if (numericFields.minOnline > numericFields.maxInstances) {
        throw new Error("Min online cannot exceed max instances.");
    }

    const runtimeProfile: BackendRuntimeProfile | undefined = supportsRuntimeProfile(values.type)
        ? values.runtimeProfile || undefined
        : undefined;

    return {
        templateBucket: values.templateBucket.trim(),
        templatePath: values.templatePath.trim(),
        templateVersion: values.templateVersion.trim(),
        jvmFlags: values.jvmFlags.trim(),
        drainTimeoutSeconds: numericFields.drainTimeoutSeconds,
        serverImage: values.serverImage.trim(),
        scaling: {
            minOnline: numericFields.minOnline,
            maxInstances: numericFields.maxInstances,
            playersPerServer: numericFields.playersPerServer,
            scaleUpThreshold: numericFields.scaleUpThreshold,
            scaleDownThreshold: numericFields.scaleDownThreshold,
            cooldownSeconds: numericFields.cooldownSeconds,
        },
        resources: {
            memoryRequest: values.resources.memoryRequest.trim(),
            memoryLimit: values.resources.memoryLimit.trim(),
            cpuRequest: values.resources.cpuRequest.trim(),
            cpuLimit: values.resources.cpuLimit.trim(),
        },
        ...(runtimeProfile ? { runtimeProfile } : {}),
        ...(values.type.toUpperCase() === "STATIC"
            ? { storageSize: values.storageSize.trim() }
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
