import {useCallback, useEffect, useState} from "react";
import {motion} from "motion/react";
import {FiPlus, FiShield, FiStar, FiX} from "react-icons/fi";
import {Link} from "react-router";

import AppToasts from "@/components/AppToasts";
import PermissionGroupFormFields from "@/components/PermissionGroupFormFields";
import {createPermissionGroup, listAllPermissionGroups} from "@/lib/api";
import {useAuthStore} from "@/store/auth-store";
import {useNetworkSettingsStore} from "@/store/network-settings-store";
import type {CreatePermissionGroupPayload, PermissionGroupFormValues, PermissionGroupRecord,} from "@/types/permission";

const REFRESH_INTERVAL_MS = 10_000;
const GROUP_THUMBNAILS = [
    "/static/thumbnails/thumbnail-1.jpg",
    "/static/thumbnails/thumbnail-2.jpg",
    "/static/thumbnails/thumbnail-3.jpg",
    "/static/thumbnails/thumbnail-4.jpg",
    "/static/thumbnails/thumbnail-5.jpg",
    "/static/thumbnails/thumbnail-6.jpg",
] as const;

const createEmptyPermissionGroup = (): PermissionGroupFormValues => ({
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

const PermissionsPage = () => {
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled
    );

    const [groups, setGroups] = useState<PermissionGroupRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isCreating, setIsCreating] = useState(false);
    const [createValues, setCreateValues] = useState<PermissionGroupFormValues>(
        createEmptyPermissionGroup()
    );
    const [groupThumbnailMap, setGroupThumbnailMap] = useState<Record<string, string>>({});

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadPermissionGroups = useCallback(async (showLoading = true) => {
        if (showLoading) {
            setIsLoading(true);
        }

        try {
            const accessToken = await getValidAccessToken();
            const nextGroups = await listAllPermissionGroups(accessToken);
            setGroups(
                [...nextGroups].sort(
                    (left, right) => left.weight - right.weight || left.name.localeCompare(right.name)
                )
            );
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load permission groups."
            );
        } finally {
            setIsLoading(false);
        }
    }, [getValidAccessToken]);

    useEffect(() => {
        let active = true;

        const runLoad = async (showLoading = true) => {
            if (!active) {
                return;
            }

            await loadPermissionGroups(showLoading);
        };

        void runLoad(true);

        const intervalId = window.setInterval(() => {
            void runLoad(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            active = false;
            window.clearInterval(intervalId);
        };
    }, [loadPermissionGroups]);

    useEffect(() => {
        if (!successMessage) {
            return;
        }

        const timeoutId = window.setTimeout(() => {
            setSuccessMessage(null);
        }, 3000);

        return () => {
            window.clearTimeout(timeoutId);
        };
    }, [successMessage]);

    useEffect(() => {
        if (groups.length === 0) {
            return;
        }

        setGroupThumbnailMap((currentMap) => {
            const nextMap = {...currentMap};
            let didChange = false;

            for (const group of groups) {
                if (!nextMap[group.id]) {
                    nextMap[group.id] =
                        GROUP_THUMBNAILS[Math.floor(Math.random() * GROUP_THUMBNAILS.length)];
                    didChange = true;
                }
            }

            return didChange ? nextMap : currentMap;
        });
    }, [groups]);

    const setTopLevelField = (
        field: Exclude<keyof PermissionGroupFormValues, "display" | "default">,
        value: string
    ) => {
        setCreateValues((currentValue) => ({
            ...currentValue,
            [field]: value,
        }));
    };

    const setDisplayField = (
        field: keyof PermissionGroupFormValues["display"],
        value: string
    ) => {
        setCreateValues((currentValue) => ({
            ...currentValue,
            display: {
                ...currentValue.display,
                [field]: value,
            },
        }));
    };

    const buildCreatePayload = (
        values: PermissionGroupFormValues
    ): CreatePermissionGroupPayload => {
        const normalizedId = values.id.trim();
        const normalizedName = values.name.trim();
        const parsedWeight = Number.parseInt(values.weight, 10);

        if (!normalizedId) {
            throw new Error("Group ID is required.");
        }

        if (!normalizedName) {
            throw new Error("Display name is required.");
        }

        if (!Number.isFinite(parsedWeight)) {
            throw new Error("Weight must be a valid number.");
        }

        return {
            id: normalizedId,
            name: normalizedName,
            weight: parsedWeight,
            default: values.default,
            display: {
                chatPrefix: values.display.chatPrefix,
                chatSuffix: values.display.chatSuffix,
                nameColor: values.display.nameColor,
                tabPrefix: values.display.tabPrefix,
            },
            permissions: [],
        };
    };

    const closeCreateModal = () => {
        setIsCreateModalOpen(false);
        setCreateValues(createEmptyPermissionGroup());
    };

    const handleCreatePermissionGroup = async () => {
        if (!permissionSystemEnabled) {
            setErrorMessage("Permission system is disabled in network settings.");
            return;
        }

        setIsCreating(true);
        setErrorMessage(null);

        try {
            const payload = buildCreatePayload(createValues);
            const accessToken = await getValidAccessToken();
            const createdGroup = await createPermissionGroup(accessToken, payload);
            setSuccessMessage(`Created permission group ${createdGroup.name}.`);
            closeCreateModal();
            await loadPermissionGroups(false);
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Unable to create permission group."
            );
        } finally {
            setIsCreating(false);
        }
    };

    return (
        <div className="space-y-8">
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                            {
                                id: "permissions-error",
                                message: errorMessage,
                                onDismiss: () => setErrorMessage(null),
                                tone: "error" as const,
                            },
                        ]
                        : []),
                    ...(successMessage
                        ? [
                            {
                                id: "permissions-success",
                                message: successMessage,
                                onDismiss: () => setSuccessMessage(null),
                                tone: "success" as const,
                            },
                        ]
                        : []),
                ]}
            />

            <motion.section
                initial={{y: 12, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut"}}
                className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between"
            >
                <div>
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiShield className="h-5 w-5 text-primary"/>
                        Permission Groups
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Manage roles, weights, formatting, and permission assignments.
                    </p>
                </div>
                <button
                    type="button"
                    disabled={!permissionSystemEnabled}
                    onClick={() => setIsCreateModalOpen(true)}
                    className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                    title={permissionSystemEnabled ? "Create Group" : "Disabled"}
                >
                    <FiPlus className="h-4 w-4"/>
                    Create Group
                </button>
            </motion.section>

            {!permissionSystemEnabled ? (
                <div className="rounded-xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-200">
                    Permission system is disabled. Changes are unavailable until it is enabled in Network settings.
                </div>
            ) : null}

            <motion.section
                initial={{y: 16, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut", delay: 0.05}}
            >
                <div className="grid grid-cols-1 gap-6 md:grid-cols-2 2xl:grid-cols-4">
                    {!isLoading && groups.length === 0 ? (
                        <div
                            className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400 md:col-span-2 2xl:col-span-4">
                            No permission groups exist yet.
                        </div>
                    ) : (
                        groups.map((group) => (
                            <Link
                                key={group.id}
                                to={`/permissions/${encodeURIComponent(group.id)}`}
                                className="group overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm transition-colors hover:border-primary/30"
                            >
                                <div className="relative h-28 overflow-hidden bg-slate-800">
                                    <img
                                        src={groupThumbnailMap[group.id] ?? GROUP_THUMBNAILS[0]}
                                        alt=""
                                        className="h-full w-full object-cover opacity-65 transition-transform duration-300 group-hover:scale-105"
                                    />
                                    <div
                                        className="absolute inset-0 bg-linear-to-b from-slate-950/0 via-slate-950/10 via-35% to-slate-900"/>
                                    {group.default && (
                                        <div className="absolute left-4 top-4">
                      <span
                          className="inline-flex items-center gap-1 rounded bg-primary/10 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-primary">
                        <FiStar className="h-3 w-3"/>
                        Default
                      </span>
                                        </div>
                                    )}
                                    <div className="absolute inset-x-4 bottom-2">
                                        <h3 className="text-base font-bold text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.55)]">
                                            {group.name}
                                        </h3>
                                        <p className="mt-0.5 font-mono text-[11px] text-slate-300 drop-shadow-[0_1px_2px_rgba(0,0,0,0.55)]">
                                            {group.id}
                                        </p>
                                    </div>
                                </div>

                                <div className="space-y-4 p-5">
                                    <div className="grid grid-cols-2 gap-4 text-sm">
                                        <div>
                                            <p className="text-slate-500">Weight</p>
                                            <p className="mt-1 flex min-h-6 items-center font-semibold text-slate-200">
                                                {group.weight}
                                            </p>
                                        </div>
                                        <div>
                                            <p className="text-slate-500">Permissions</p>
                                            <p className="mt-1 flex min-h-6 items-center font-semibold text-slate-200">
                                                {group.permissions.length}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                                            Name Color
                                        </p>
                                        <p className="mt-1.5 truncate text-sm font-semibold text-slate-100">
                                            {group.display.nameColor || "--"}
                                        </p>
                                    </div>

                                    <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                                            Tab Prefix
                                        </p>
                                        <p className="mt-1.5 truncate text-sm font-semibold text-slate-100">
                                            {group.display.tabPrefix || "--"}
                                        </p>
                                    </div>
                                </div>
                            </Link>
                        ))
                    )}
                </div>
            </motion.section>

            {isCreateModalOpen && (
                <div
                    className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                    <motion.div
                        initial={{y: 12, opacity: 0}}
                        animate={{y: 0, opacity: 1}}
                        transition={{duration: 0.25, ease: "easeOut"}}
                        className="flex max-h-[92vh] w-full max-w-4xl flex-col overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                    >
                        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                            <div>
                                <h3 className="text-base font-semibold text-white">
                                    Create Permission Group
                                </h3>
                                <p className="text-sm text-slate-400">
                                    Define a new role with formatting and a default weight.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={closeCreateModal}
                                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                                aria-label="Close"
                            >
                                <FiX className="h-4 w-4"/>
                            </button>
                        </div>

                        <div className="flex-1 overflow-y-auto p-6">
                            <PermissionGroupFormFields
                                values={createValues}
                                onFieldChange={setTopLevelField}
                                onDisplayChange={setDisplayField}
                                onDefaultChange={(value) =>
                                    setCreateValues((currentValue) => ({
                                        ...currentValue,
                                        default: value,
                                    }))
                                }
                            />
                        </div>

                        <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
                            <button
                                type="button"
                                onClick={closeCreateModal}
                                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                disabled={isCreating || !permissionSystemEnabled}
                                onClick={() => void handleCreatePermissionGroup()}
                                className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {isCreating ? "Creating..." : "Create Group"}
                            </button>
                        </div>
                    </motion.div>
                </div>
            )}
        </div>
    );
};

export default PermissionsPage;
