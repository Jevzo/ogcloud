import { z } from "zod";

import { isServerImageCompatible, supportsRuntimeProfile } from "@/lib/group-runtime";
import { GROUP_TYPE_VALUES } from "@/types/group";
import { BACKEND_RUNTIME_PROFILE_VALUES } from "@/types/runtime";

export const groupScalingConfigSchema = z.object({
    minOnline: z.number().int().nonnegative(),
    maxInstances: z.number().int().positive(),
    playersPerServer: z.number().int().positive(),
    scaleUpThreshold: z.number().finite(),
    scaleDownThreshold: z.number().finite(),
    cooldownSeconds: z.number().int().positive(),
});

export const groupResourceConfigSchema = z.object({
    memoryRequest: z.string().min(1),
    memoryLimit: z.string().min(1),
    cpuRequest: z.string().min(1),
    cpuLimit: z.string().min(1),
});

export const groupRecordSchema = z.object({
    id: z.string().min(1),
    type: z.enum(GROUP_TYPE_VALUES),
    templateBucket: z.string().min(1),
    templatePath: z.string().min(1),
    templateVersion: z.string().min(1),
    scaling: groupScalingConfigSchema,
    resources: groupResourceConfigSchema,
    jvmFlags: z.string(),
    drainTimeoutSeconds: z.number().int().positive(),
    serverImage: z.string().min(1),
    runtimeProfile: z.enum(BACKEND_RUNTIME_PROFILE_VALUES).nullable(),
    storageSize: z.string().min(1),
    maintenance: z.boolean(),
    createdAt: z.string().min(1),
    updatedAt: z.string().min(1),
});

export const groupListItemSchema = z.object({
    id: z.string().min(1),
    type: z.enum(GROUP_TYPE_VALUES),
    maintenance: z.boolean(),
});

const runtimeProfileFieldSchema = z.union([
    z.enum(BACKEND_RUNTIME_PROFILE_VALUES),
    z.literal(""),
]);

export const groupFormScalingSchema = z.object({
    minOnline: z.string().trim().min(1, "Enter the minimum online instance count."),
    maxInstances: z.string().trim().min(1, "Enter the maximum instance count."),
    playersPerServer: z.string().trim().min(1, "Enter the players per server target."),
    scaleUpThreshold: z.string().trim().min(1, "Enter the scale-up threshold."),
    scaleDownThreshold: z.string().trim().min(1, "Enter the scale-down threshold."),
    cooldownSeconds: z.string().trim().min(1, "Enter the autoscaling cooldown."),
});

export const groupFormResourceSchema = z.object({
    memoryRequest: z.string().trim().min(1, "Memory request is required."),
    memoryLimit: z.string().trim().min(1, "Memory limit is required."),
    cpuRequest: z.string().trim().min(1, "CPU request is required."),
    cpuLimit: z.string().trim().min(1, "CPU limit is required."),
});

const addCustomIssue = (
    context: z.RefinementCtx,
    path: (string | number)[],
    message: string,
) => {
    context.addIssue({
        code: z.ZodIssueCode.custom,
        message,
        path,
    });
};

const parseIntegerField = (
    context: z.RefinementCtx,
    path: (string | number)[],
    label: string,
    value: string,
    {
        allowZero = false,
    }: {
        allowZero?: boolean;
    } = {},
) => {
    const parsedValue = Number.parseInt(value, 10);

    if (!Number.isFinite(parsedValue)) {
        addCustomIssue(context, path, `${label} must be a valid number.`);
        return null;
    }

    if (allowZero ? parsedValue < 0 : parsedValue <= 0) {
        addCustomIssue(
            context,
            path,
            allowZero ? `${label} cannot be negative.` : `${label} must be greater than zero.`,
        );
        return null;
    }

    return parsedValue;
};

const parseFloatField = (
    context: z.RefinementCtx,
    path: (string | number)[],
    label: string,
    value: string,
) => {
    const parsedValue = Number.parseFloat(value);

    if (!Number.isFinite(parsedValue)) {
        addCustomIssue(context, path, `${label} must be a valid number.`);
        return null;
    }

    return parsedValue;
};

export const groupFormSchema = z
    .object({
        id: z.string().trim().min(1, "Group name is required."),
        type: z.enum(GROUP_TYPE_VALUES),
        templateBucket: z.string().trim().min(1, "Template bucket is required."),
        templatePath: z.string().trim().min(1, "Template is required."),
        templateVersion: z.string().trim().min(1, "Template version is required."),
        jvmFlags: z.string().trim().min(1, "JVM flags are required."),
        drainTimeoutSeconds: z.string().trim().min(1, "Drain timeout is required."),
        serverImage: z.string().trim().min(1, "Server image is required."),
        runtimeProfile: runtimeProfileFieldSchema,
        storageSize: z.string().trim(),
        scaling: groupFormScalingSchema,
        resources: groupFormResourceSchema,
    })
    .superRefine((values, context) => {
        const isStaticGroup = values.type === "STATIC";
        const hasRuntimeProfile = supportsRuntimeProfile(values.type);
        const runtimeProfile = hasRuntimeProfile ? values.runtimeProfile : "";

        if (isStaticGroup && !values.storageSize) {
            addCustomIssue(context, ["storageSize"], "Storage size is required for STATIC groups.");
        }

        if (hasRuntimeProfile && !runtimeProfile) {
            addCustomIssue(
                context,
                ["runtimeProfile"],
                "Runtime profile is required for backend groups.",
            );
        }

        if (!isServerImageCompatible(values.type, runtimeProfile, values.serverImage)) {
            if (values.type === "PROXY") {
                addCustomIssue(
                    context,
                    ["serverImage"],
                    "Proxy groups must use a velocity image.",
                );
            } else if (runtimeProfile === "LEGACY_1_8_8") {
                addCustomIssue(
                    context,
                    ["serverImage"],
                    "Legacy 1.8.8 groups must use ogwarsdev/paper:1.8.8.",
                );
            } else {
                addCustomIssue(
                    context,
                    ["serverImage"],
                    "Modern backend groups must use a paper image tagged 1.21.11.",
                );
            }
        }

        const minOnline = parseIntegerField(
            context,
            ["scaling", "minOnline"],
            "Min online",
            values.scaling.minOnline,
            { allowZero: true },
        );
        const maxInstances = parseIntegerField(
            context,
            ["scaling", "maxInstances"],
            "Max instances",
            values.scaling.maxInstances,
        );

        parseIntegerField(
            context,
            ["drainTimeoutSeconds"],
            "Drain timeout",
            values.drainTimeoutSeconds,
        );
        parseIntegerField(
            context,
            ["scaling", "playersPerServer"],
            "Players per server",
            values.scaling.playersPerServer,
        );
        parseFloatField(
            context,
            ["scaling", "scaleUpThreshold"],
            "Scale-up threshold",
            values.scaling.scaleUpThreshold,
        );
        parseFloatField(
            context,
            ["scaling", "scaleDownThreshold"],
            "Scale-down threshold",
            values.scaling.scaleDownThreshold,
        );
        parseIntegerField(
            context,
            ["scaling", "cooldownSeconds"],
            "Cooldown",
            values.scaling.cooldownSeconds,
        );

        if (
            typeof minOnline === "number" &&
            typeof maxInstances === "number" &&
            minOnline > maxInstances
        ) {
            addCustomIssue(
                context,
                ["scaling", "minOnline"],
                "Min online cannot exceed max instances.",
            );
        }
    });

export type GroupRecordSchema = z.infer<typeof groupRecordSchema>;
export type GroupListItemSchema = z.infer<typeof groupListItemSchema>;
export type GroupFormSchema = z.infer<typeof groupFormSchema>;
