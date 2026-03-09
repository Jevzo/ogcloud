import {useCallback, useEffect, useState} from "react";
import {motion} from "motion/react";
import {FiLayers, FiPlus, FiX} from "react-icons/fi";
import {Link} from "react-router";

import AppToasts from "@/components/AppToasts";
import GroupFormFields from "@/components/GroupFormFields";
import {createServerGroup, listAllServerGroups, listAllTemplates,} from "@/lib/api";
import {useAuthStore} from "@/store/auth-store";
import type {CreateGroupPayload, GroupFormValues, GroupRecord,} from "@/types/group";
import type {TemplateRecord} from "@/types/template";

const GROUP_THUMBNAILS = [
    "/static/thumbnails/thumbnail-1.jpg",
    "/static/thumbnails/thumbnail-2.jpg",
    "/static/thumbnails/thumbnail-3.jpg",
    "/static/thumbnails/thumbnail-4.jpg",
    "/static/thumbnails/thumbnail-5.jpg",
    "/static/thumbnails/thumbnail-6.jpg",
] as const;
const REFRESH_INTERVAL_MS = 10_000;

const getRandomGroupThumbnail = () =>
    GROUP_THUMBNAILS[Math.floor(Math.random() * GROUP_THUMBNAILS.length)];

const getGroupModeTone = (mode: string) =>
    mode.toUpperCase() === "STATIC"
        ? "bg-amber-400 text-slate-950"
        : "bg-primary text-slate-950";

const applyFirstTemplateDefaults = (
    values: GroupFormValues,
    templates: TemplateRecord[],
) => {
    const firstTemplate = templates[0];

    if (!firstTemplate) {
        return values;
    }

    return {
        ...values,
        templatePath: firstTemplate.group,
        templateVersion: firstTemplate.version,
    };
};

const getPlayersPerServerByGroupType = (groupType: string) => {
    const normalizedType = groupType.toUpperCase();

    if (normalizedType === "STATIC") {
        return "100";
    }

    if (normalizedType === "PROXY") {
        return "512";
    }

    return "50";
};

const createEmptyGroupValues = (): GroupFormValues => ({
    id: "",
    type: "DYNAMIC",
    templateBucket: "ogcloud-templates",
    templatePath: "ogcloud-templates",
    templateVersion: "",
    jvmFlags: "-Xms512M -Xmx512M",
    drainTimeoutSeconds: "30",
    serverImage: "ogwarsdev/paper:latest",
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

const GroupsPage = () => {
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);

    const [groups, setGroups] = useState<GroupRecord[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isCreating, setIsCreating] = useState(false);
    const [templates, setTemplates] = useState<TemplateRecord[]>([]);
    const [createValues, setCreateValues] = useState<GroupFormValues>(
        createEmptyGroupValues(),
    );
    const [groupThumbnailMap, setGroupThumbnailMap] = useState<
        Record<string, string>
    >({});

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadGroups = useCallback(async (showLoading = true) => {
        if (showLoading) {
            setIsLoading(true);
        }

        try {
            const accessToken = await getValidAccessToken();
            const nextGroups = await listAllServerGroups(accessToken);
            setGroups(nextGroups);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load groups.",
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

            await loadGroups(showLoading);
        };

        void runLoad(true);

        const intervalId = window.setInterval(() => {
            void runLoad(false);
        }, REFRESH_INTERVAL_MS);

        return () => {
            active = false;
            window.clearInterval(intervalId);
        };
    }, [loadGroups]);

    useEffect(() => {
        let active = true;

        const loadTemplates = async () => {
            try {
                const accessToken = await getValidAccessToken();
                const nextTemplates = await listAllTemplates(accessToken);

                if (!active) {
                    return;
                }

                setTemplates(nextTemplates);
                setCreateValues((currentValue) => {
                    if (currentValue.templatePath || nextTemplates.length === 0) {
                        return currentValue;
                    }

                    return applyFirstTemplateDefaults(currentValue, nextTemplates);
                });
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

    useEffect(() => {
        if (groups.length === 0) {
            return;
        }

        setGroupThumbnailMap((currentMap) => {
            const nextMap = {...currentMap};
            let didChange = false;

            for (const group of groups) {
                if (!nextMap[group.id]) {
                    nextMap[group.id] = getRandomGroupThumbnail();
                    didChange = true;
                }
            }

            return didChange ? nextMap : currentMap;
        });
    }, [groups]);

    const setTopLevelField = (
        field: Exclude<keyof GroupFormValues, "scaling" | "resources">,
        value: string,
    ) => {
        setCreateValues((currentValue) => {
            if (field === "type") {
                return {
                    ...currentValue,
                    type: value,
                    scaling: {
                        ...currentValue.scaling,
                        playersPerServer: getPlayersPerServerByGroupType(value),
                    },
                };
            }

            return {
                ...currentValue,
                [field]: value,
            };
        });
    };

    const setScalingField = (
        field: keyof GroupFormValues["scaling"],
        value: string,
    ) => {
        setCreateValues((currentValue) => ({
            ...currentValue,
            scaling: {
                ...currentValue.scaling,
                [field]: value,
            },
        }));
    };

    const setResourceField = (
        field: keyof GroupFormValues["resources"],
        value: string,
    ) => {
        setCreateValues((currentValue) => ({
            ...currentValue,
            resources: {
                ...currentValue.resources,
                [field]: value,
            },
        }));
    };

    const handleTemplateChange = (templatePath: string) => {
        const [templateGroup, templateVersion] = templatePath.split("::");
        const nextTemplate = templates.find(
            (template) =>
                template.group === templateGroup &&
                template.version === templateVersion,
        );

        if (!nextTemplate) {
            return;
        }

        setCreateValues((currentValue) => ({
            ...currentValue,
            templatePath: nextTemplate.group,
            templateVersion: nextTemplate.version,
        }));
    };

    const buildCreatePayload = (values: GroupFormValues): CreateGroupPayload => {
        const requiredFields = [
            ["Group name", values.id],
            ["Template bucket", values.templateBucket],
            ["Template", values.templatePath],
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

        return {
            id: values.id.trim(),
            type: values.type,
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
            ...(values.type.toUpperCase() === "STATIC"
                ? {storageSize: values.storageSize.trim()}
                : {}),
        };
    };

    const closeCreateModal = () => {
        setIsCreateModalOpen(false);
        setCreateValues(
            applyFirstTemplateDefaults(createEmptyGroupValues(), templates),
        );
    };

    const handleCreateGroup = async () => {
        setErrorMessage(null);
        setIsCreating(true);

        try {
            const payload = buildCreatePayload(createValues);
            const accessToken = await getValidAccessToken();
            const createdGroup = await createServerGroup(accessToken, payload);
            setSuccessMessage(`Created server group ${createdGroup.id}.`);
            closeCreateModal();
            await loadGroups(false);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to create group.",
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
                                id: "groups-error",
                                message: errorMessage,
                                onDismiss: () => setErrorMessage(null),
                                tone: "error" as const,
                            },
                        ]
                        : []),
                    ...(successMessage
                        ? [
                            {
                                id: "groups-success",
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
                        <FiLayers className="h-5 w-5 text-primary"/>
                        Server Groups
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Manage server-group templates, scaling rules, and runtime capacity.
                    </p>
                </div>
                <button
                    type="button"
                    onClick={() => setIsCreateModalOpen(true)}
                    className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950"
                >
                    <FiPlus className="h-4 w-4"/>
                    Create Group
                </button>
            </motion.section>

            <motion.section
                initial={{y: 16, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut", delay: 0.05}}
            >
                <div className="grid grid-cols-1 gap-6 md:grid-cols-2 2xl:grid-cols-4">
                    {!isLoading && groups.length === 0 ? (
                        <div
                            className="rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400 md:col-span-2 2xl:col-span-4">
                            No server groups exist yet.
                        </div>
                    ) : (
                        groups.map((group) => (
                            <Link
                                key={group.id}
                                to={`/groups/${encodeURIComponent(group.id)}`}
                                className="group overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm transition-colors hover:border-primary/30"
                            >
                                <div className="relative h-32 overflow-hidden bg-slate-800">
                                    <img
                                        src={
                                            groupThumbnailMap[group.id] ?? getRandomGroupThumbnail()
                                        }
                                        alt=""
                                        className="h-full w-full object-cover opacity-65 transition-transform duration-300 group-hover:scale-105"
                                    />
                                    <div
                                        className="absolute inset-0 bg-linear-to-b from-slate-950/0 via-slate-950/12 via-35% to-slate-900"/>
                                    <div className="absolute left-4 top-4 flex items-center gap-2">
                    <span
                        className={`rounded px-2 py-0.5 text-[10px] font-bold uppercase ${getGroupModeTone(
                            group.type,
                        )}`}
                    >
                      {group.type}
                    </span>
                                        {group.maintenance && (
                                            <span
                                                className="rounded bg-amber-500/12 px-2 py-0.5 text-[10px] font-bold uppercase text-amber-300">
                        Maintenance
                      </span>
                                        )}
                                    </div>
                                    <div className="absolute inset-x-4 bottom-2">
                                        <h3 className="text-base font-bold text-white drop-shadow-[0_1px_2px_rgba(0,0,0,0.55)]">
                                            {group.id}
                                        </h3>
                                    </div>
                                </div>

                                <div className="space-y-3 p-4">
                                    <div className="grid grid-cols-2 gap-3 text-sm">
                                        <div>
                                            <p className="text-slate-500">Min Instances</p>
                                            <p className="mt-1 font-semibold text-slate-200">
                                                {group.scaling.minOnline}
                                            </p>
                                        </div>
                                        <div>
                                            <p className="text-slate-500">Max Instances</p>
                                            <p className="mt-1 font-semibold text-slate-200">
                                                {group.scaling.maxInstances}
                                            </p>
                                        </div>
                                        <div>
                                            <p className="text-slate-500">Template</p>
                                            <p className="mt-1 truncate font-semibold text-slate-200">
                                                {group.templatePath}/{group.templateVersion}
                                            </p>
                                        </div>
                                        <div>
                                            <p className="text-slate-500">Players/Server</p>
                                            <p className="mt-1 font-semibold text-slate-200">
                                                {group.scaling.playersPerServer}
                                            </p>
                                        </div>
                                    </div>
                                    <div className="rounded-lg border border-slate-800 bg-slate-950/35 px-4 py-3">
                                        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                                            Running Image
                                        </p>
                                        <p className="mt-1.5 truncate text-sm font-semibold text-slate-100">
                                            {group.serverImage}
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
                        className="flex max-h-[92vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
                    >
                        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
                            <div>
                                <h3 className="text-base font-semibold text-white">
                                    Create Server Group
                                </h3>
                                <p className="text-sm text-slate-400">
                                    Define a new deployable server group and its scaling profile.
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
                            <GroupFormFields
                                values={createValues}
                                onFieldChange={setTopLevelField}
                                onScalingChange={setScalingField}
                                onResourceChange={setResourceField}
                                templates={templates}
                                onTemplateChange={handleTemplateChange}
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
                                disabled={isCreating}
                                onClick={() => void handleCreateGroup()}
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

export default GroupsPage;
