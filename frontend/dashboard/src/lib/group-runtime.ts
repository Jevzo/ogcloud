import type { GroupType } from "@/types/group";
import type { BackendRuntimeProfile, RuntimeBundleScope } from "@/types/runtime";

export const VELOCITY_SERVER_IMAGE = "ogwarsdev/velocity:latest";
export const MODERN_PAPER_SERVER_IMAGE = "ogwarsdev/paper:1.21.11";
export const LEGACY_PAPER_SERVER_IMAGE = "ogwarsdev/paper:1.8.8";

interface RuntimeProfileOption {
    value: BackendRuntimeProfile;
    label: string;
    description: string;
    minecraftVersion: string;
    serverImage: string;
    scope: RuntimeBundleScope;
}

export const BACKEND_RUNTIME_PROFILE_OPTIONS: readonly RuntimeProfileOption[] = [
    {
        value: "MODERN_1_21_11",
        label: "Modern Paper 1.21.11",
        description: "Managed runtime for current backend groups.",
        minecraftVersion: "1.21.11",
        serverImage: MODERN_PAPER_SERVER_IMAGE,
        scope: "PAPER_1_21_11",
    },
    {
        value: "LEGACY_1_8_8",
        label: "Legacy Paper 1.8.8",
        description: "Managed runtime for legacy-compatible backend groups.",
        minecraftVersion: "1.8.8",
        serverImage: LEGACY_PAPER_SERVER_IMAGE,
        scope: "PAPER_1_8_8",
    },
] as const;

interface RuntimeRefreshOption {
    scope: RuntimeBundleScope;
    label: string;
    description: string;
}

export const RUNTIME_REFRESH_OPTIONS: readonly RuntimeRefreshOption[] = [
    {
        scope: "VELOCITY",
        label: "Velocity Runtime",
        description: "Refresh managed proxy runtime assets for all velocity groups.",
    },
    {
        scope: "PAPER_1_21_11",
        label: "Paper 1.21.11",
        description: "Refresh managed modern paper runtime assets for backend groups.",
    },
    {
        scope: "PAPER_1_8_8",
        label: "Paper 1.8.8",
        description: "Refresh managed legacy paper runtime assets for backend groups.",
    },
] as const;

const DEFAULT_BACKEND_RUNTIME_PROFILE: BackendRuntimeProfile = "MODERN_1_21_11";

const findRuntimeProfileOption = (runtimeProfile: BackendRuntimeProfile) =>
    BACKEND_RUNTIME_PROFILE_OPTIONS.find((option) => option.value === runtimeProfile) ?? null;

const DEFAULT_BACKEND_RUNTIME_PROFILE_OPTION =
    findRuntimeProfileOption(DEFAULT_BACKEND_RUNTIME_PROFILE) ?? BACKEND_RUNTIME_PROFILE_OPTIONS[0];

export const supportsRuntimeProfile = (groupType: GroupType | string) =>
    groupType.trim().toUpperCase() !== "PROXY";

export const getDefaultRuntimeProfile = (
    groupType: GroupType | string,
): BackendRuntimeProfile | "" =>
    supportsRuntimeProfile(groupType) ? DEFAULT_BACKEND_RUNTIME_PROFILE : "";

export const getDefaultServerImage = (
    groupType: GroupType | string,
    runtimeProfile: BackendRuntimeProfile | "",
) => {
    if (!supportsRuntimeProfile(groupType)) {
        return VELOCITY_SERVER_IMAGE;
    }

    return findRuntimeProfileOption(
        runtimeProfile || DEFAULT_BACKEND_RUNTIME_PROFILE,
    )?.serverImage ?? MODERN_PAPER_SERVER_IMAGE;
};

const imageName = (serverImage: string) => {
    const normalized = serverImage.split("@", 1)[0] ?? serverImage;
    const lastColon = normalized.lastIndexOf(":");
    const lastSlash = normalized.lastIndexOf("/");

    return lastColon > lastSlash
        ? normalized.slice(0, lastColon).toLowerCase()
        : normalized.toLowerCase();
};

const imageTag = (serverImage: string) => {
    const normalized = serverImage.split("@", 1)[0] ?? serverImage;
    const lastColon = normalized.lastIndexOf(":");
    const lastSlash = normalized.lastIndexOf("/");

    return lastColon > lastSlash ? normalized.slice(lastColon + 1) : "";
};

export const isServerImageCompatible = (
    groupType: GroupType | string,
    runtimeProfile: BackendRuntimeProfile | "",
    serverImage: string,
) => {
    if (!serverImage.trim()) {
        return false;
    }

    if (!supportsRuntimeProfile(groupType)) {
        const name = imageName(serverImage);
        return name === "velocity" || name.endsWith("/velocity");
    }

    const name = imageName(serverImage);
    if (!(name === "paper" || name.endsWith("/paper"))) {
        return false;
    }

    if (runtimeProfile === "LEGACY_1_8_8") {
        return serverImage === LEGACY_PAPER_SERVER_IMAGE;
    }

    return imageTag(serverImage) === DEFAULT_BACKEND_RUNTIME_PROFILE_OPTION.minecraftVersion;
};

export const coerceRuntimeSelection = (
    groupType: GroupType | string,
    runtimeProfile: BackendRuntimeProfile | "",
    serverImage: string,
) => {
    const resolvedRuntimeProfile: BackendRuntimeProfile | "" = supportsRuntimeProfile(groupType)
        ? runtimeProfile || getDefaultRuntimeProfile(groupType) || DEFAULT_BACKEND_RUNTIME_PROFILE
        : "";
    const nextServerImage = isServerImageCompatible(groupType, resolvedRuntimeProfile, serverImage)
        ? serverImage
        : getDefaultServerImage(groupType, resolvedRuntimeProfile);

    return {
        runtimeProfile: resolvedRuntimeProfile,
        serverImage: nextServerImage,
    };
};

export const getServerImageOptions = (
    groupType: GroupType | string,
    runtimeProfile: BackendRuntimeProfile | "",
    currentServerImage: string,
) => {
    const defaultServerImage = getDefaultServerImage(groupType, runtimeProfile);

    if (
        currentServerImage.trim() &&
        currentServerImage !== defaultServerImage &&
        isServerImageCompatible(groupType, runtimeProfile, currentServerImage)
    ) {
        return [currentServerImage, defaultServerImage];
    }

    return [defaultServerImage];
};

export const getRuntimeProfileLabel = (runtimeProfile: BackendRuntimeProfile | null) => {
    if (!runtimeProfile) {
        return "Managed Velocity";
    }

    return findRuntimeProfileOption(runtimeProfile)?.label ?? runtimeProfile;
};
