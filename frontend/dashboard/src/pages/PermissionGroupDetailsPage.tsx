import { useCallback, useEffect, useState } from "react";
import { motion } from "motion/react";
import {
    FiArrowLeft,
    FiChevronLeft,
    FiChevronRight,
    FiPlus,
    FiShield,
    FiTrash2,
    FiX,
} from "react-icons/fi";
import { Link, useNavigate, useParams } from "react-router";

import AppToasts from "@/components/AppToasts";
import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import PermissionGroupFormFields from "@/components/PermissionGroupFormFields";
import { deletePermissionGroup, getPermissionGroupByName, updatePermissionGroup } from "@/lib/api";
import { useAuthStore } from "@/store/auth-store";
import { useNetworkSettingsStore } from "@/store/network-settings-store";
import type {
    PermissionGroupFormValues,
    PermissionGroupRecord,
    UpdatePermissionGroupPayload,
} from "@/types/permission";

const PERMISSIONS_PAGE_SIZE = 10;

const toFormValues = (group: PermissionGroupRecord): PermissionGroupFormValues => ({
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

const PermissionGroupDetailsPage = () => {
    const params = useParams();
    const navigate = useNavigate();
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const groupName = decodeURIComponent(params.groupName ?? "");

    const [group, setGroup] = useState<PermissionGroupRecord | null>(null);
    const [formValues, setFormValues] = useState<PermissionGroupFormValues | null>(null);
    const [permissionsDraft, setPermissionsDraft] = useState<string[]>([]);
    const [newPermission, setNewPermission] = useState("");
    const [permissionPageIndex, setPermissionPageIndex] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isDeletingGroup, setIsDeletingGroup] = useState(false);
    const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadPermissionGroup = useCallback(
        async (showLoading = true) => {
            if (!groupName) {
                setErrorMessage("Missing permission group name.");
                setIsLoading(false);
                return;
            }

            if (showLoading) {
                setIsLoading(true);
            }

            try {
                const accessToken = await getValidAccessToken();
                const nextGroup = await getPermissionGroupByName(accessToken, groupName);
                setGroup(nextGroup);
                setFormValues(toFormValues(nextGroup));
                setPermissionsDraft(nextGroup.permissions);
                setErrorMessage(null);
            } catch (error) {
                setErrorMessage(
                    error instanceof Error ? error.message : "Unable to load permission group.",
                );
            } finally {
                setIsLoading(false);
            }
        },
        [getValidAccessToken, groupName],
    );

    useEffect(() => {
        void loadPermissionGroup(true);
    }, [loadPermissionGroup]);

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
        setPermissionPageIndex(0);
    }, [groupName]);

    useEffect(() => {
        const maxPageIndex = Math.max(
            0,
            Math.ceil(permissionsDraft.length / PERMISSIONS_PAGE_SIZE) - 1,
        );

        setPermissionPageIndex((currentValue) => Math.min(currentValue, maxPageIndex));
    }, [permissionsDraft.length]);

    const setTopLevelField = (
        field: Exclude<keyof PermissionGroupFormValues, "display" | "default">,
        value: string,
    ) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                      ...currentValue,
                      [field]: value,
                  }
                : currentValue,
        );
    };

    const setDisplayField = (field: keyof PermissionGroupFormValues["display"], value: string) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                      ...currentValue,
                      display: {
                          ...currentValue.display,
                          [field]: value,
                      },
                  }
                : currentValue,
        );
    };

    const buildUpdatePayload = (
        values: PermissionGroupFormValues,
    ): UpdatePermissionGroupPayload => {
        const normalizedName = values.name.trim();
        const parsedWeight = Number.parseInt(values.weight, 10);

        if (!normalizedName) {
            throw new Error("Display name is required.");
        }

        if (!Number.isFinite(parsedWeight)) {
            throw new Error("Weight must be a valid number.");
        }

        return {
            name: normalizedName,
            weight: parsedWeight,
            default: values.default,
            display: {
                chatPrefix: values.display.chatPrefix,
                chatSuffix: values.display.chatSuffix,
                nameColor: values.display.nameColor,
                tabPrefix: values.display.tabPrefix,
            },
        };
    };

    const syncGroupState = (nextGroup: PermissionGroupRecord) => {
        setGroup(nextGroup);
        setPermissionsDraft(nextGroup.permissions);
        setFormValues((currentValue) =>
            currentValue
                ? {
                      ...currentValue,
                      name: nextGroup.name,
                      weight: String(nextGroup.weight),
                      default: nextGroup.default,
                      display: nextGroup.display,
                  }
                : toFormValues(nextGroup),
        );
    };

    const handleSaveGroup = async () => {
        if (!group || !formValues) {
            return;
        }

        if (!permissionSystemEnabled) {
            setErrorMessage("Permission system is disabled in network settings.");
            return;
        }

        setIsSaving(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const updatedGroup = await updatePermissionGroup(accessToken, group.id, {
                ...buildUpdatePayload(formValues),
                permissions: permissionsDraft,
            });
            syncGroupState(updatedGroup);
            setSuccessMessage(`Updated permission group ${updatedGroup.name}.`);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to update permission group.",
            );
        } finally {
            setIsSaving(false);
        }
    };

    const handleAddPermission = async () => {
        if (!group || !permissionSystemEnabled) {
            if (!permissionSystemEnabled) {
                setErrorMessage("Permission system is disabled in network settings.");
            }
            return;
        }

        const normalizedPermission = newPermission.trim();

        if (!normalizedPermission) {
            setErrorMessage("Enter a permission node first.");
            return;
        }

        if (permissionsDraft.includes(normalizedPermission)) {
            setErrorMessage("Permission already exists in this group.");
            return;
        }

        setPermissionsDraft((currentValue) => [...currentValue, normalizedPermission]);
        setNewPermission("");
        setErrorMessage(null);
    };

    const handleRemovePermission = (permission: string) => {
        if (!group || !permissionSystemEnabled) {
            if (!permissionSystemEnabled) {
                setErrorMessage("Permission system is disabled in network settings.");
            }
            return;
        }

        setPermissionsDraft((currentValue) =>
            currentValue.filter((currentPermission) => currentPermission !== permission),
        );
        setErrorMessage(null);
    };

    const handleDeleteGroup = async () => {
        if (!group) {
            return;
        }

        if (!permissionSystemEnabled) {
            setErrorMessage("Permission system is disabled in network settings.");
            return;
        }

        setIsDeletingGroup(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            await deletePermissionGroup(accessToken, group.id);
            navigate("/permissions", { replace: true });
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to delete permission group.",
            );
            setIsDeletingGroup(false);
            setIsDeleteModalOpen(false);
        }
    };

    const totalPermissionPages = Math.max(
        1,
        Math.ceil(permissionsDraft.length / PERMISSIONS_PAGE_SIZE),
    );
    const visiblePermissions = permissionsDraft.slice(
        permissionPageIndex * PERMISSIONS_PAGE_SIZE,
        permissionPageIndex * PERMISSIONS_PAGE_SIZE + PERMISSIONS_PAGE_SIZE,
    );
    const previewGroupName = group?.name?.trim() || group?.id?.trim() || "Group";
    const previewNameColor = group?.display.nameColor?.trim() || "&7";
    const previewChatPrefix = group?.display.chatPrefix?.trim() || "&7[Group] ";
    const previewChatSuffix = group?.display.chatSuffix?.trim() || "&7: &f";
    const previewTabPrefix = group?.display.tabPrefix?.trim() || "&7";
    const displayPreviewFallbacks = {
        chatPrefix: `${previewChatPrefix}${previewNameColor}${previewGroupName}`,
        chatSuffix: `${previewNameColor}${previewGroupName}${previewChatSuffix}Hello`,
        nameColor: `${previewNameColor}${previewGroupName}`,
        tabPrefix: `${previewTabPrefix}${previewNameColor}${previewGroupName}`,
    } as const;

    return (
        <div className="space-y-8">
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                              {
                                  id: "permission-detail-error",
                                  message: errorMessage,
                                  onDismiss: () => setErrorMessage(null),
                                  tone: "error" as const,
                              },
                          ]
                        : []),
                    ...(successMessage
                        ? [
                              {
                                  id: "permission-detail-success",
                                  message: successMessage,
                                  onDismiss: () => setSuccessMessage(null),
                                  tone: "success" as const,
                              },
                          ]
                        : []),
                ]}
            />

            <motion.section
                initial={{ y: 12, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut" }}
                className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
            >
                <div>
                    <Link
                        to="/permissions"
                        className="inline-flex items-center gap-2 text-sm font-medium text-slate-400 transition-colors hover:text-primary"
                    >
                        <FiArrowLeft className="h-4 w-4" />
                        Back to permission groups
                    </Link>
                    <h2 className="mt-3 flex items-center gap-2 text-lg font-bold text-white">
                        <FiShield className="h-5 w-5 text-primary" />
                        {group?.name || groupName || "Permission Group"}
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Update formatting, weight, default handling, and explicit permission nodes.
                    </p>
                </div>

                <button
                    type="button"
                    disabled={!group || !permissionSystemEnabled}
                    onClick={() => setIsDeleteModalOpen(true)}
                    className="app-button-field button-hover-lift button-shadow-danger inline-flex items-center gap-2 rounded-lg bg-red-500/10 px-4 py-2.5 text-sm font-semibold text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                    title={permissionSystemEnabled ? "Delete Group" : "Disabled"}
                >
                    <FiTrash2 className="h-4 w-4" />
                    Delete Group
                </button>
            </motion.section>

            {!permissionSystemEnabled ? (
                <div className="rounded-xl border border-amber-500/20 bg-amber-500/10 px-4 py-3 text-sm text-amber-200">
                    Permission system is disabled. This page is read-only until it is enabled in
                    Network settings.
                </div>
            ) : null}

            <motion.section
                initial={{ y: 16, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
                className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]"
            >
                <div className="space-y-6">
                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Group Overview
                            </h3>
                        </div>
                        <div className="grid grid-cols-1 gap-3 p-6 md:grid-cols-2">
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Group ID
                                </p>
                                <p className="mt-1.5 break-all font-mono text-xs font-semibold text-slate-100">
                                    {group?.id || "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Default
                                </p>
                                <p
                                    className={`mt-1.5 text-sm font-semibold ${
                                        group?.default ? "text-emerald-300" : "text-slate-200"
                                    }`}
                                >
                                    {group?.default ? "Yes" : "No"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Weight
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group?.weight ?? "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Permissions
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group?.permissions.length ?? "--"}
                                </p>
                            </div>
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Current Display Values
                            </h3>
                        </div>
                        <div className="space-y-4 p-6">
                            <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Chat Prefix
                                </p>
                                <MinecraftTextPreview
                                    value={group?.display.chatPrefix}
                                    fallback={displayPreviewFallbacks.chatPrefix}
                                    emptyFallback="Not set"
                                    useFallbackForFormatOnly
                                    className="mt-2 font-mono"
                                />
                            </div>
                            <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                    Chat Suffix
                                </p>
                                <MinecraftTextPreview
                                    value={group?.display.chatSuffix}
                                    fallback={displayPreviewFallbacks.chatSuffix}
                                    emptyFallback="Not set"
                                    useFallbackForFormatOnly
                                    className="mt-2 font-mono"
                                />
                            </div>
                            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                                <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Name Color
                                    </p>
                                    <MinecraftTextPreview
                                        value={group?.display.nameColor}
                                        fallback={displayPreviewFallbacks.nameColor}
                                        emptyFallback="Not set"
                                        useFallbackForFormatOnly
                                        className="mt-2 font-mono"
                                    />
                                </div>
                                <div className="rounded-lg border border-slate-700/70 bg-linear-to-br from-slate-900 via-slate-900 to-slate-950 p-4">
                                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Tab Prefix
                                    </p>
                                    <MinecraftTextPreview
                                        value={group?.display.tabPrefix}
                                        fallback={displayPreviewFallbacks.tabPrefix}
                                        emptyFallback="Not set"
                                        useFallbackForFormatOnly
                                        className="mt-2 font-mono"
                                    />
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Permission List
                            </h3>
                        </div>
                        <div className="space-y-5 p-6">
                            <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                                <input
                                    type="text"
                                    value={newPermission}
                                    onChange={(event) => setNewPermission(event.target.value)}
                                    disabled={!permissionSystemEnabled || isSaving}
                                    className="app-input-field min-w-0 flex-1 rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10"
                                    placeholder="ogcloud.command.execute"
                                />
                                <button
                                    type="button"
                                    disabled={!permissionSystemEnabled || isSaving}
                                    onClick={() => void handleAddPermission()}
                                    className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                                >
                                    <FiPlus className="h-4 w-4" />
                                    Add
                                </button>
                            </div>

                            <div className="rounded-lg border border-amber-500/20 bg-amber-500/8 px-3 py-2 text-xs text-amber-200">
                                Adding or removing permissions takes immediate effect across online
                                players after you save this group.
                            </div>

                            <div className="space-y-3">
                                {permissionsDraft.length === 0 ? (
                                    <div className="rounded-lg border border-slate-800 bg-slate-800/30 px-4 py-4 text-sm text-slate-400">
                                        No explicit permissions assigned.
                                    </div>
                                ) : (
                                    visiblePermissions.map((permission) => (
                                        <div
                                            key={permission}
                                            className="flex min-h-9 items-center gap-2.5 rounded-lg border border-slate-800 bg-slate-800/30 px-3 py-1.5"
                                        >
                                            <span className="flex min-h-4 min-w-0 flex-1 items-center break-all font-mono text-sm text-slate-200">
                                                {permission}
                                            </span>
                                            <button
                                                type="button"
                                                disabled={!permissionSystemEnabled || isSaving}
                                                onClick={() => handleRemovePermission(permission)}
                                                className="button-hover-lift inline-flex h-7 w-7 items-center justify-center rounded-lg bg-red-500/10 text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                                                aria-label={`Remove ${permission}`}
                                            >
                                                <FiTrash2 className="h-3.5 w-3.5" />
                                            </button>
                                        </div>
                                    ))
                                )}
                            </div>

                            {permissionsDraft.length > PERMISSIONS_PAGE_SIZE && (
                                <div className="flex flex-col gap-3 border-t border-slate-800 pt-4 sm:flex-row sm:items-center sm:justify-between">
                                    <span className="text-sm text-slate-400">
                                        Page {permissionPageIndex + 1} of {totalPermissionPages}
                                    </span>
                                    <div className="flex items-center gap-2">
                                        <button
                                            type="button"
                                            disabled={permissionPageIndex === 0}
                                            onClick={() =>
                                                setPermissionPageIndex((value) =>
                                                    Math.max(0, value - 1),
                                                )
                                            }
                                            className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                                        >
                                            <FiChevronLeft className="h-4 w-4" />
                                            Previous
                                        </button>
                                        <button
                                            type="button"
                                            disabled={
                                                permissionPageIndex >= totalPermissionPages - 1
                                            }
                                            onClick={() =>
                                                setPermissionPageIndex((value) =>
                                                    Math.min(totalPermissionPages - 1, value + 1),
                                                )
                                            }
                                            className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                                        >
                                            Next
                                            <FiChevronRight className="h-4 w-4" />
                                        </button>
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm self-start">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Editable Configuration
                        </h3>
                    </div>

                    <div className="p-6">
                        {isLoading || !formValues ? (
                            <p className="text-sm text-slate-400">
                                Loading permission group configuration...
                            </p>
                        ) : (
                            <div className="space-y-6">
                                <PermissionGroupFormFields
                                    values={formValues}
                                    onFieldChange={setTopLevelField}
                                    onDisplayChange={setDisplayField}
                                    onDefaultChange={(value) =>
                                        setFormValues((currentValue) =>
                                            currentValue
                                                ? {
                                                      ...currentValue,
                                                      default: value,
                                                  }
                                                : currentValue,
                                        )
                                    }
                                    disableIdentityFields
                                />

                                <div className="flex justify-end">
                                    <button
                                        type="button"
                                        disabled={isSaving || !permissionSystemEnabled}
                                        onClick={() => void handleSaveGroup()}
                                        className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                                    >
                                        {isSaving ? "Saving..." : "Save Changes"}
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            </motion.section>

            {isDeleteModalOpen && group && (
                <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                    <motion.div
                        initial={{ y: 12, opacity: 0 }}
                        animate={{ y: 0, opacity: 1 }}
                        transition={{ duration: 0.25, ease: "easeOut" }}
                        className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                    >
                        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                            <div>
                                <h3 className="text-base font-semibold text-white">Delete Group</h3>
                                <p className="text-sm text-slate-400">
                                    This removes the {group.name} permission group.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setIsDeleteModalOpen(false)}
                                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                                aria-label="Close"
                            >
                                <FiX className="h-4 w-4" />
                            </button>
                        </div>

                        <div className="px-6 py-5">
                            <p className="text-sm leading-6 text-slate-300">
                                Players currently assigned to this group will be moved to the
                                default permission group after deletion.
                            </p>
                        </div>

                        <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
                            <button
                                type="button"
                                onClick={() => setIsDeleteModalOpen(false)}
                                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                disabled={isDeletingGroup}
                                onClick={() => void handleDeleteGroup()}
                                className="app-button-field button-hover-lift button-shadow-danger rounded-lg bg-red-500/10 px-4 py-2.5 text-sm font-semibold text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {isDeletingGroup ? "Deleting..." : "Delete Group"}
                            </button>
                        </div>
                    </motion.div>
                </div>
            )}
        </div>
    );
};

export default PermissionGroupDetailsPage;
