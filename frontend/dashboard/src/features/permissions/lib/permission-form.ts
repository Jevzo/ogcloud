import { permissionGroupFormSchema } from "@/features/permissions/schemas";
import type {
    CreatePermissionGroupPayload,
    PermissionGroupFormValues,
    PermissionGroupPermissionPayload,
    PermissionGroupRecord,
    UpdatePermissionGroupPayload,
} from "@/types/permission";

const validatePermissionGroupFormValues = (values: PermissionGroupFormValues) => {
    const result = permissionGroupFormSchema.safeParse(values);

    if (!result.success) {
        throw new Error(
            result.error.issues[0]?.message ?? "Invalid permission group configuration.",
        );
    }

    return result.data;
};

export const createEmptyPermissionGroupValues = (): PermissionGroupFormValues => ({
    id: "",
    name: "",
    weight: "100",
    default: false,
    display: {
        chatPrefix: "",
        chatSuffix: "&7: &f",
        nameColor: "&7",
        tabPrefix: "",
    },
});

export const toPermissionGroupFormValues = (
    group: PermissionGroupRecord,
): PermissionGroupFormValues => ({
    id: group.id,
    name: group.name,
    weight: String(group.weight),
    default: group.default,
    display: {
        chatPrefix: group.display.chatPrefix,
        chatSuffix: group.display.chatSuffix,
        nameColor: group.display.nameColor,
        tabPrefix: group.display.tabPrefix,
    },
});

const buildPermissionGroupPayloadFields = (values: PermissionGroupFormValues) => {
    const validatedValues = validatePermissionGroupFormValues(values);

    return {
        name: validatedValues.name,
        weight: Number.parseInt(validatedValues.weight, 10),
        default: validatedValues.default,
        display: {
            chatPrefix: validatedValues.display.chatPrefix,
            chatSuffix: validatedValues.display.chatSuffix,
            nameColor: validatedValues.display.nameColor,
            tabPrefix: validatedValues.display.tabPrefix,
        },
    };
};

export const buildCreatePermissionGroupPayload = (
    values: PermissionGroupFormValues,
): CreatePermissionGroupPayload => {
    const validatedValues = validatePermissionGroupFormValues(values);

    return {
        id: validatedValues.id,
        permissions: [],
        ...buildPermissionGroupPayloadFields(validatedValues),
    };
};

export const buildUpdatePermissionGroupPayload = (
    values: PermissionGroupFormValues,
    permissions?: PermissionGroupPermissionPayload[],
): UpdatePermissionGroupPayload => ({
    ...buildPermissionGroupPayloadFields(values),
    ...(permissions ? { permissions } : {}),
});
