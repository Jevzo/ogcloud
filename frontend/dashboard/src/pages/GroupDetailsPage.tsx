import { useCallback, useEffect, useState } from "react";
import { motion } from "motion/react";
import { FiAlertTriangle, FiArrowLeft, FiRefreshCw, FiShield, FiTrash2, FiX } from "react-icons/fi";
import { Link, useNavigate, useParams } from "react-router";

import AppToasts from "@/components/AppToasts";
import GroupFormFields from "@/components/GroupFormFields";
import {
    applyRuntimeProfileToFormValues,
    buildUpdateGroupPayload,
    toGroupFormValues,
} from "@/lib/group-form";
import { getRuntimeProfileLabel } from "@/lib/group-runtime";
import {
    deleteServerGroup,
    getGroupByName,
    listAllTemplates,
    listServers,
    restartServerGroup,
    toggleServerGroupMaintenance,
    updateServerGroup,
} from "@/lib/api";
import { formatDateTime } from "@/lib/server-display";
import { useAuthStore } from "@/store/auth-store";
import type { GroupFormValues, GroupRecord, UpdateGroupPayload } from "@/types/group";
import type { TemplateRecord } from "@/types/template";

const GroupDetailsPage = () => {
    const params = useParams();
    const navigate = useNavigate();
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const groupName = decodeURIComponent(params.groupName ?? "");

    const [group, setGroup] = useState<GroupRecord | null>(null);
    const [formValues, setFormValues] = useState<GroupFormValues | null>(null);
    const [templates, setTemplates] = useState<TemplateRecord[]>([]);
    const [currentOnlineCount, setCurrentOnlineCount] = useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isRestartingGroup, setIsRestartingGroup] = useState(false);
    const [isTogglingMaintenance, setIsTogglingMaintenance] = useState(false);
    const [isMaintenanceModalOpen, setIsMaintenanceModalOpen] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
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

    const loadGroup = useCallback(
        async (showLoading = true) => {
            if (!groupName) {
                setErrorMessage("Missing group name.");
                setIsLoading(false);
                return;
            }

            if (showLoading) {
                setIsLoading(true);
            }

            try {
                const accessToken = await getValidAccessToken();
                const [nextGroup, groupServers] = await Promise.all([
                    getGroupByName(accessToken, groupName),
                    listServers(accessToken, {
                        group: groupName,
                        page: 0,
                        size: 200,
                    }),
                ]);
                setGroup(nextGroup);
                setFormValues(toGroupFormValues(nextGroup));
                setCurrentOnlineCount(
                    groupServers.items.filter((server) => server.state.toUpperCase() === "RUNNING")
                        .length,
                );
                setErrorMessage(null);
            } catch (error) {
                setErrorMessage(error instanceof Error ? error.message : "Unable to load group.");
            } finally {
                setIsLoading(false);
            }
        },
        [getValidAccessToken, groupName],
    );

    useEffect(() => {
        void loadGroup(true);
    }, [loadGroup]);

    useEffect(() => {
        let active = true;

        const loadTemplates = async () => {
            try {
                const accessToken = await getValidAccessToken();
                const nextTemplates = await listAllTemplates(accessToken);

                if (active) {
                    setTemplates(nextTemplates);
                }
            } catch {
                if (active) {
                    setTemplates([]);
                }
            }
        };

        void loadTemplates();

        return () => {
            active = false;
        };
    }, [getValidAccessToken]);

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

    const setTopLevelField = (
        field: Exclude<keyof GroupFormValues, "scaling" | "resources">,
        value: string,
    ) => {
        setFormValues((currentValue) =>
            currentValue
                ? field === "runtimeProfile"
                    ? applyRuntimeProfileToFormValues(
                          currentValue,
                          value as GroupFormValues["runtimeProfile"],
                      )
                    : {
                          ...currentValue,
                          [field]: value,
                      }
                : currentValue,
        );
    };

    const setScalingField = (field: keyof GroupFormValues["scaling"], value: string) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                      ...currentValue,
                      scaling: {
                          ...currentValue.scaling,
                          [field]: value,
                      },
                  }
                : currentValue,
        );
    };

    const setResourceField = (field: keyof GroupFormValues["resources"], value: string) => {
        setFormValues((currentValue) =>
            currentValue
                ? {
                      ...currentValue,
                      resources: {
                          ...currentValue.resources,
                          [field]: value,
                      },
                  }
                : currentValue,
        );
    };

    const handleTemplateChange = (templatePath: string) => {
        const [templateGroup, templateVersion] = templatePath.split("::");
        const nextTemplate = templates.find(
            (template) => template.group === templateGroup && template.version === templateVersion,
        );

        if (!nextTemplate) {
            return;
        }

        setFormValues((currentValue) =>
            currentValue
                ? {
                      ...currentValue,
                      templatePath: nextTemplate.group,
                      templateVersion: nextTemplate.version,
                  }
                : currentValue,
        );
    };

    const handleSaveGroup = async () => {
        if (!formValues || !group) {
            return;
        }

        setIsSaving(true);
        setErrorMessage(null);

        try {
            const payload: UpdateGroupPayload = buildUpdateGroupPayload(formValues);
            const accessToken = await getValidAccessToken();
            const updatedGroup = await updateServerGroup(accessToken, group.id, payload);
            setGroup(updatedGroup);
            setFormValues(toGroupFormValues(updatedGroup));
            setSuccessMessage(`Updated group ${updatedGroup.id}.`);
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "Unable to update group.");
        } finally {
            setIsSaving(false);
        }
    };

    const handleToggleMaintenance = async () => {
        if (!group) {
            return;
        }

        if (!group.maintenance) {
            setIsMaintenanceModalOpen(true);
            return;
        }

        setIsTogglingMaintenance(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const updatedGroup = await toggleServerGroupMaintenance(accessToken, group.id, false);
            setGroup(updatedGroup);
            setFormValues(toGroupFormValues(updatedGroup));
            setSuccessMessage(`${updatedGroup.id} maintenance disabled.`);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to update maintenance.",
            );
        } finally {
            setIsTogglingMaintenance(false);
        }
    };

    const handleRestartGroup = async () => {
        if (!group) {
            return;
        }

        setIsRestartingGroup(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            await restartServerGroup(accessToken, group.id);
            setSuccessMessage(`${group.id} restart requested.`);
            await loadGroup(false);
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "Unable to restart group.");
        } finally {
            setIsRestartingGroup(false);
        }
    };

    const handleConfirmEnableMaintenance = async () => {
        if (!group) {
            return;
        }

        setIsTogglingMaintenance(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            const updatedGroup = await toggleServerGroupMaintenance(accessToken, group.id, true);
            setGroup(updatedGroup);
            setFormValues(toGroupFormValues(updatedGroup));
            setSuccessMessage(`${updatedGroup.id} maintenance enabled.`);
            setIsMaintenanceModalOpen(false);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to update maintenance.",
            );
        } finally {
            setIsTogglingMaintenance(false);
        }
    };

    const handleDeleteGroup = async () => {
        if (!group) {
            return;
        }

        setIsDeleting(true);
        setErrorMessage(null);

        try {
            const accessToken = await getValidAccessToken();
            await deleteServerGroup(accessToken, group.id);
            navigate("/groups", { replace: true });
        } catch (error) {
            setErrorMessage(error instanceof Error ? error.message : "Unable to delete group.");
            setIsDeleting(false);
            setIsDeleteModalOpen(false);
        }
    };

    return (
        <div className="space-y-8">
            <AppToasts
                items={[
                    ...(errorMessage
                        ? [
                              {
                                  id: "group-detail-error",
                                  message: errorMessage,
                                  onDismiss: () => setErrorMessage(null),
                                  tone: "error" as const,
                              },
                          ]
                        : []),
                    ...(successMessage
                        ? [
                              {
                                  id: "group-detail-success",
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
                        to="/groups"
                        className="inline-flex items-center gap-2 text-sm font-medium text-slate-400 transition-colors hover:text-primary"
                    >
                        <FiArrowLeft className="h-4 w-4" />
                        Back to groups
                    </Link>
                    <h2 className="mt-3 text-lg font-bold text-white">
                        {group?.id || groupName || "Group Details"}
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Update template settings, scaling thresholds, resources, and lifecycle.
                    </p>
                </div>

                <div className="flex flex-wrap gap-2">
                    <button
                        type="button"
                        disabled={isRestartingGroup || !group}
                        onClick={() => void handleRestartGroup()}
                        className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary/10 px-4 py-2.5 text-sm font-semibold text-primary disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        <FiRefreshCw
                            className={`h-4 w-4 ${isRestartingGroup ? "animate-spin" : ""}`}
                        />
                        {isRestartingGroup ? "Restarting..." : "Restart Group"}
                    </button>
                    <button
                        type="button"
                        disabled={isTogglingMaintenance || !group}
                        onClick={() => void handleToggleMaintenance()}
                        className={`app-button-field button-hover-lift inline-flex items-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${
                            group?.maintenance
                                ? "button-shadow-neutral border border-slate-700 text-slate-200"
                                : "button-shadow-warning bg-amber-500/12 text-amber-300"
                        }`}
                    >
                        <FiShield className="h-4 w-4" />
                        {isTogglingMaintenance
                            ? "Updating..."
                            : group?.maintenance
                              ? "Disable Maintenance"
                              : "Enable Maintenance"}
                    </button>
                    <button
                        type="button"
                        disabled={!group}
                        onClick={() => setIsDeleteModalOpen(true)}
                        className="app-button-field button-hover-lift button-shadow-danger inline-flex items-center gap-2 rounded-lg bg-red-500/10 px-4 py-2.5 text-sm font-semibold text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                    >
                        <FiTrash2 className="h-4 w-4" />
                        Delete Group
                    </button>
                </div>
            </motion.section>

            {isMaintenanceModalOpen && group && (
                <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
                    <motion.div
                        initial={{ y: 12, opacity: 0 }}
                        animate={{ y: 0, opacity: 1 }}
                        transition={{ duration: 0.25, ease: "easeOut" }}
                        className="w-full max-w-md rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                    >
                        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                            <div>
                                <h3 className="text-base font-semibold text-white">
                                    Enable Group Maintenance
                                </h3>
                                <p className="text-sm text-slate-400">
                                    This affects the {group.id} group immediately.
                                </p>
                            </div>
                            <button
                                type="button"
                                onClick={() => setIsMaintenanceModalOpen(false)}
                                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                                aria-label="Close"
                            >
                                <FiX className="h-4 w-4" />
                            </button>
                        </div>

                        <div className="space-y-4 px-6 py-5">
                            <div className="rounded-lg border border-red-500/20 bg-red-500/10 px-4 py-4 text-sm text-red-100">
                                <div className="flex items-start gap-3">
                                    <FiAlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-red-300" />
                                    <p>
                                        Enabling maintenance can disconnect active players from this
                                        group and block new joins until you disable it again.
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
                            <button
                                type="button"
                                onClick={() => setIsMaintenanceModalOpen(false)}
                                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
                            >
                                Cancel
                            </button>
                            <button
                                type="button"
                                disabled={isTogglingMaintenance}
                                onClick={() => void handleConfirmEnableMaintenance()}
                                className="app-button-field button-hover-lift button-shadow-warning rounded-lg bg-amber-500/12 px-4 py-2.5 text-sm font-semibold text-amber-300 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {isTogglingMaintenance ? "Enabling..." : "Enable Maintenance"}
                            </button>
                        </div>
                    </motion.div>
                </div>
            )}

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
                                    Type
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group?.type || "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Maintenance
                                </p>
                                <p
                                    className={`mt-1.5 text-sm font-semibold ${
                                        group?.maintenance ? "text-amber-300" : "text-emerald-300"
                                    }`}
                                >
                                    {group ? (group.maintenance ? "Enabled" : "Disabled") : "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Runtime
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group ? getRuntimeProfileLabel(group.runtimeProfile) : "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Created
                                </p>
                                <p className="mt-1.5 text-sm font-medium text-slate-200">
                                    {group ? formatDateTime(group.createdAt) : "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Last Updated
                                </p>
                                <p className="mt-1.5 text-sm font-medium text-slate-200">
                                    {group ? formatDateTime(group.updatedAt) : "--"}
                                </p>
                            </div>
                        </div>
                    </div>

                    <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                Scaling Snapshot
                            </h3>
                        </div>
                        <div className="grid grid-cols-1 gap-3 p-6 md:grid-cols-2">
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Min Online
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group?.scaling.minOnline ?? "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Max Instances
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group?.scaling.maxInstances ?? "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Players Per Server
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group?.scaling.playersPerServer ?? "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Currently Online
                                </p>
                                <p
                                    className={`mt-1.5 text-sm font-semibold ${
                                        typeof currentOnlineCount === "number" &&
                                        currentOnlineCount > 0
                                            ? "text-emerald-300"
                                            : "text-slate-100"
                                    }`}
                                >
                                    {currentOnlineCount ?? "--"}
                                </p>
                            </div>
                            <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3 md:col-span-2">
                                <p className="text-[11px] font-semibold uppercase tracking-[0.16em] text-slate-500">
                                    Cooldown
                                </p>
                                <p className="mt-1.5 text-sm font-semibold text-slate-100">
                                    {group ? `${group.scaling.cooldownSeconds}s` : "--"}
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                <div className="rounded-xl border border-slate-800 bg-slate-900 shadow-sm">
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                            Editable Configuration
                        </h3>
                    </div>

                    <div className="p-6">
                        {isLoading || !formValues ? (
                            <p className="text-sm text-slate-400">Loading group configuration...</p>
                        ) : (
                            <div className="space-y-6">
                                <GroupFormFields
                                    values={formValues}
                                    onFieldChange={setTopLevelField}
                                    onScalingChange={setScalingField}
                                    onResourceChange={setResourceField}
                                    templates={templates}
                                    onTemplateChange={handleTemplateChange}
                                    disableIdentityFields
                                />

                                <div className="flex justify-end">
                                    <button
                                        type="button"
                                        disabled={isSaving}
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
                                    This permanently removes the {group.id} server group.
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
                                Existing servers in this group may fail to deploy or scale after
                                this action. Make sure the group is no longer needed before deleting
                                it.
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
                                disabled={isDeleting}
                                onClick={() => void handleDeleteGroup()}
                                className="app-button-field button-hover-lift button-shadow-danger rounded-lg bg-red-500/10 px-4 py-2.5 text-sm font-semibold text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                {isDeleting ? "Deleting..." : "Delete Group"}
                            </button>
                        </div>
                    </motion.div>
                </div>
            )}
        </div>
    );
};

export default GroupDetailsPage;
