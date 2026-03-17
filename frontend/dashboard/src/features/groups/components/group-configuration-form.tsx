import { type ReactNode } from "react";
import { Controller, useWatch, type UseFormReturn } from "react-hook-form";

import { Badge } from "@/components/ui/badge";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import {
    applyGroupTypeToFormValues,
    applyRuntimeProfileToFormValues,
} from "@/lib/group-form";
import {
    BACKEND_RUNTIME_PROFILE_OPTIONS,
    getRuntimeProfileLabel,
    getServerImageOptions,
    supportsRuntimeProfile,
} from "@/lib/group-runtime";
import type { GroupFormValues } from "@/types/group";
import type { TemplateRecord } from "@/types/template";

const SECTION_CLASS_NAME = "space-y-4 rounded-xl border border-border/70 bg-muted/25 p-4";

const Section = ({
    children,
    description,
    title,
}: {
    children: ReactNode;
    description: string;
    title: string;
}) => (
    <section className={SECTION_CLASS_NAME}>
        <div className="space-y-1">
            <h3 className="text-sm font-semibold text-foreground">{title}</h3>
            <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        {children}
    </section>
);

interface GroupConfigurationFormProps {
    disableIdentityFields?: boolean;
    form: UseFormReturn<GroupFormValues>;
    showIdentityFields?: boolean;
    templates: TemplateRecord[];
}

const GroupConfigurationForm = ({
    disableIdentityFields = false,
    form,
    showIdentityFields = true,
    templates,
}: GroupConfigurationFormProps) => {
    const groupType = useWatch({ control: form.control, name: "type" }) ?? form.getValues("type");
    const runtimeProfile =
        useWatch({ control: form.control, name: "runtimeProfile" }) ??
        form.getValues("runtimeProfile");
    const templatePath =
        useWatch({ control: form.control, name: "templatePath" }) ??
        form.getValues("templatePath");
    const templateVersion =
        useWatch({ control: form.control, name: "templateVersion" }) ??
        form.getValues("templateVersion");
    const serverImage =
        useWatch({ control: form.control, name: "serverImage" }) ??
        form.getValues("serverImage");
    const isStaticGroup = groupType === "STATIC";
    const isProxyGroup = !supportsRuntimeProfile(groupType);
    const runtimeDescription = BACKEND_RUNTIME_PROFILE_OPTIONS.find(
        (option) => option.value === runtimeProfile,
    )?.description;
    const selectedTemplateValue =
        templatePath && templateVersion ? `${templatePath}::${templateVersion}` : "";
    const selectedTemplateExists = templates.some(
        (template) => `${template.group}::${template.version}` === selectedTemplateValue,
    );
    const serverImageOptions = getServerImageOptions(groupType, runtimeProfile, serverImage);
    const selectedServerImageExists = !serverImage || serverImageOptions.includes(serverImage);

    const applyTypeSelection = (nextType: GroupFormValues["type"]) => {
        const nextValues = applyGroupTypeToFormValues(form.getValues(), nextType);

        form.setValue("type", nextValues.type, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
        form.setValue("runtimeProfile", nextValues.runtimeProfile, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
        form.setValue("serverImage", nextValues.serverImage, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
        form.setValue("scaling.playersPerServer", nextValues.scaling.playersPerServer, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
    };

    const applyRuntimeSelection = (nextRuntimeProfile: GroupFormValues["runtimeProfile"]) => {
        const nextValues = applyRuntimeProfileToFormValues(form.getValues(), nextRuntimeProfile);

        form.setValue("runtimeProfile", nextValues.runtimeProfile, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
        form.setValue("serverImage", nextValues.serverImage, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
    };

    const applyTemplateSelection = (templateValue: string) => {
        const [nextTemplatePath, nextTemplateVersion] = templateValue.split("::");

        if (!nextTemplatePath || !nextTemplateVersion) {
            return;
        }

        form.setValue("templatePath", nextTemplatePath, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
        form.setValue("templateVersion", nextTemplateVersion, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
    };

    return (
        <div className="space-y-4">
            {showIdentityFields ? (
                <Section
                    title="Identity"
                    description="Core group metadata used for routing, deployment, and lifecycle control."
                >
                    <FieldGroup className="grid gap-4 md:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="group-id">Group name</FieldLabel>
                            <Input
                                id="group-id"
                                disabled={disableIdentityFields}
                                aria-invalid={form.formState.errors.id ? "true" : "false"}
                                {...form.register("id")}
                            />
                            <FieldDescription>
                                Unique ID used across routes, deployments, and runtime actions.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.id]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="group-type">Group type</FieldLabel>
                            <Controller
                                control={form.control}
                                name="type"
                                render={({ field }) => (
                                    <Select
                                        value={field.value}
                                        onValueChange={(value) =>
                                            applyTypeSelection(value as GroupFormValues["type"])
                                        }
                                        disabled={disableIdentityFields}
                                    >
                                        <SelectTrigger id="group-type" className="w-full">
                                            <SelectValue placeholder="Select a group type" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="DYNAMIC">DYNAMIC</SelectItem>
                                            <SelectItem value="STATIC">STATIC</SelectItem>
                                            <SelectItem value="PROXY">PROXY</SelectItem>
                                        </SelectContent>
                                    </Select>
                                )}
                            />
                            <FieldDescription>
                                `DYNAMIC` scales automatically, `STATIC` is persistent, and `PROXY`
                                handles network ingress.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.type]} />
                        </Field>
                    </FieldGroup>
                </Section>
            ) : null}

            <Section
                title="Template and runtime"
                description="Define how new instances are bootstrapped and which managed runtime they use."
            >
                <FieldGroup className="grid gap-4 md:grid-cols-2">
                    <Field>
                        <FieldLabel htmlFor="group-template-bucket">Template bucket</FieldLabel>
                        <Input
                            id="group-template-bucket"
                            aria-invalid={
                                form.formState.errors.templateBucket ? "true" : "false"
                            }
                            {...form.register("templateBucket")}
                        />
                        <FieldDescription>
                            Object storage bucket containing template archives.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.templateBucket]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-template-known">Known template</FieldLabel>
                        <Select
                            value={selectedTemplateValue || undefined}
                            onValueChange={(value) => {
                                applyTemplateSelection(value);
                            }}
                            disabled={templates.length === 0}
                        >
                            <SelectTrigger id="group-template-known" className="w-full">
                                <SelectValue
                                    placeholder={
                                        templates.length === 0
                                            ? "No template catalog available"
                                            : "Select a known template"
                                    }
                                />
                            </SelectTrigger>
                            <SelectContent>
                                {!selectedTemplateExists && selectedTemplateValue ? (
                                    <SelectItem value={selectedTemplateValue}>
                                        {templatePath} / {templateVersion}
                                    </SelectItem>
                                ) : null}
                                {templates.map((template) => (
                                    <SelectItem
                                        key={`${template.group}-${template.version}`}
                                        value={`${template.group}::${template.version}`}
                                    >
                                        {template.group} / {template.version}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <FieldDescription>
                            Optional shortcut for filling the template path and version below.
                        </FieldDescription>
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-template-path">Template path</FieldLabel>
                        <Input
                            id="group-template-path"
                            aria-invalid={form.formState.errors.templatePath ? "true" : "false"}
                            {...form.register("templatePath")}
                        />
                        <FieldDescription>
                            Template source used when bootstrapping new instances in this group.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.templatePath]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-template-version">Template version</FieldLabel>
                        <Input
                            id="group-template-version"
                            aria-invalid={
                                form.formState.errors.templateVersion ? "true" : "false"
                            }
                            {...form.register("templateVersion")}
                        />
                        <FieldDescription>
                            Version or tag of the template archive to deploy.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.templateVersion]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-runtime-profile">
                            {isProxyGroup ? "Proxy runtime" : "Backend runtime"}
                        </FieldLabel>
                        {isProxyGroup ? (
                            <div className="flex h-8 items-center rounded-lg border border-border bg-background px-2.5 text-sm text-foreground">
                                {getRuntimeProfileLabel(null)}
                            </div>
                        ) : (
                            <Controller
                                control={form.control}
                                name="runtimeProfile"
                                render={({ field }) => (
                                    <Select
                                        value={field.value}
                                        onValueChange={(value) =>
                                            applyRuntimeSelection(
                                                value as GroupFormValues["runtimeProfile"],
                                            )
                                        }
                                    >
                                        <SelectTrigger
                                            id="group-runtime-profile"
                                            className="w-full"
                                        >
                                            <SelectValue placeholder="Select a runtime profile" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {BACKEND_RUNTIME_PROFILE_OPTIONS.map((option) => (
                                                <SelectItem key={option.value} value={option.value}>
                                                    {option.label}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                )}
                            />
                        )}
                        <FieldDescription>
                            {isProxyGroup
                                ? "Proxy groups always use the managed Velocity runtime."
                                : "Select the managed runtime profile required for backend deployments."}
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.runtimeProfile]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-server-image">Server image</FieldLabel>
                        <Controller
                            control={form.control}
                            name="serverImage"
                            render={({ field }) => (
                                <Select
                                    value={field.value}
                                    onValueChange={(value) => field.onChange(value)}
                                >
                                    <SelectTrigger id="group-server-image" className="w-full">
                                        <SelectValue placeholder="Select a server image" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {!selectedServerImageExists && serverImage ? (
                                            <SelectItem value={serverImage}>
                                                {serverImage}
                                            </SelectItem>
                                        ) : null}
                                        {serverImageOptions.map((option) => (
                                            <SelectItem key={option} value={option}>
                                                {option}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            )}
                        />
                        <FieldDescription>
                            Container image paired with the selected runtime profile and group type.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.serverImage]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-drain-timeout">
                            Drain timeout (seconds)
                        </FieldLabel>
                        <Input
                            id="group-drain-timeout"
                            type="number"
                            min={1}
                            step={1}
                            aria-invalid={
                                form.formState.errors.drainTimeoutSeconds ? "true" : "false"
                            }
                            {...form.register("drainTimeoutSeconds")}
                        />
                        <FieldDescription>
                            Grace period before forcefully stopping a draining server.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.drainTimeoutSeconds]} />
                    </Field>

                    {isStaticGroup ? (
                        <Field>
                            <FieldLabel htmlFor="group-storage-size">Storage size</FieldLabel>
                            <Input
                                id="group-storage-size"
                                aria-invalid={
                                    form.formState.errors.storageSize ? "true" : "false"
                                }
                                {...form.register("storageSize")}
                            />
                            <FieldDescription>
                                Persistent volume size reserved for STATIC group servers.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.storageSize]} />
                        </Field>
                    ) : null}
                </FieldGroup>

                {!isProxyGroup && runtimeDescription ? (
                    <div className="flex flex-wrap items-center gap-2 rounded-xl border border-primary/20 bg-primary/5 px-3 py-2 text-sm text-muted-foreground">
                        <Badge variant="outline" className="border-primary/30 bg-primary/10 text-primary">
                            Managed runtime
                        </Badge>
                        {runtimeDescription}
                    </div>
                ) : null}

                <Field>
                    <FieldLabel htmlFor="group-jvm-flags">JVM flags</FieldLabel>
                    <Input
                        id="group-jvm-flags"
                        aria-invalid={form.formState.errors.jvmFlags ? "true" : "false"}
                        {...form.register("jvmFlags")}
                    />
                    <FieldDescription>
                        JVM startup options passed directly to the Minecraft process.
                    </FieldDescription>
                    <FieldError errors={[form.formState.errors.jvmFlags]} />
                </Field>
            </Section>

            <Section
                title="Scaling"
                description="Tune autoscaling thresholds, caps, and player density targets."
            >
                <FieldGroup className="grid gap-4 md:grid-cols-3">
                    <Field>
                        <FieldLabel htmlFor="group-min-online">Min online</FieldLabel>
                        <Input
                            id="group-min-online"
                            type="number"
                            min={0}
                            step={1}
                            aria-invalid={
                                form.formState.errors.scaling?.minOnline ? "true" : "false"
                            }
                            {...form.register("scaling.minOnline")}
                        />
                        <FieldDescription>
                            Minimum number of instances kept available at all times.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.scaling?.minOnline]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-max-instances">Max instances</FieldLabel>
                        <Input
                            id="group-max-instances"
                            type="number"
                            min={1}
                            step={1}
                            aria-invalid={
                                form.formState.errors.scaling?.maxInstances ? "true" : "false"
                            }
                            {...form.register("scaling.maxInstances")}
                        />
                        <FieldDescription>
                            Hard autoscaling cap for simultaneously running instances.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.scaling?.maxInstances]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-players-per-server">
                            Players per server
                        </FieldLabel>
                        <Input
                            id="group-players-per-server"
                            type="number"
                            min={1}
                            step={1}
                            aria-invalid={
                                form.formState.errors.scaling?.playersPerServer
                                    ? "true"
                                    : "false"
                            }
                            {...form.register("scaling.playersPerServer")}
                        />
                        <FieldDescription>
                            Target player capacity used for balancing and scaling calculations.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.scaling?.playersPerServer]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-scale-up-threshold">
                            Scale-up threshold
                        </FieldLabel>
                        <Input
                            id="group-scale-up-threshold"
                            type="number"
                            step="0.01"
                            aria-invalid={
                                form.formState.errors.scaling?.scaleUpThreshold
                                    ? "true"
                                    : "false"
                            }
                            {...form.register("scaling.scaleUpThreshold")}
                        />
                        <FieldDescription>
                            Utilization level that triggers a scale-up action.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.scaling?.scaleUpThreshold]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-scale-down-threshold">
                            Scale-down threshold
                        </FieldLabel>
                        <Input
                            id="group-scale-down-threshold"
                            type="number"
                            step="0.01"
                            aria-invalid={
                                form.formState.errors.scaling?.scaleDownThreshold
                                    ? "true"
                                    : "false"
                            }
                            {...form.register("scaling.scaleDownThreshold")}
                        />
                        <FieldDescription>
                            Utilization level below which downscaling can occur.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.scaling?.scaleDownThreshold]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-cooldown-seconds">Cooldown (seconds)</FieldLabel>
                        <Input
                            id="group-cooldown-seconds"
                            type="number"
                            min={1}
                            step={1}
                            aria-invalid={
                                form.formState.errors.scaling?.cooldownSeconds
                                    ? "true"
                                    : "false"
                            }
                            {...form.register("scaling.cooldownSeconds")}
                        />
                        <FieldDescription>
                            Minimum wait time between consecutive autoscaling operations.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.scaling?.cooldownSeconds]} />
                    </Field>
                </FieldGroup>
            </Section>

            <Section
                title="Resources"
                description="Set Kubernetes memory and CPU requests and limits for group instances."
            >
                <FieldGroup className="grid gap-4 md:grid-cols-2">
                    <Field>
                        <FieldLabel htmlFor="group-memory-request">Memory request</FieldLabel>
                        <Input
                            id="group-memory-request"
                            aria-invalid={
                                form.formState.errors.resources?.memoryRequest ? "true" : "false"
                            }
                            {...form.register("resources.memoryRequest")}
                        />
                        <FieldDescription>
                            Guaranteed memory reserved for the pod by Kubernetes.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.resources?.memoryRequest]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-memory-limit">Memory limit</FieldLabel>
                        <Input
                            id="group-memory-limit"
                            aria-invalid={
                                form.formState.errors.resources?.memoryLimit ? "true" : "false"
                            }
                            {...form.register("resources.memoryLimit")}
                        />
                        <FieldDescription>
                            Maximum memory available before the container can be OOM-killed.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.resources?.memoryLimit]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-cpu-request">CPU request</FieldLabel>
                        <Input
                            id="group-cpu-request"
                            aria-invalid={
                                form.formState.errors.resources?.cpuRequest ? "true" : "false"
                            }
                            {...form.register("resources.cpuRequest")}
                        />
                        <FieldDescription>
                            Guaranteed CPU reserved for the pod scheduler.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.resources?.cpuRequest]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="group-cpu-limit">CPU limit</FieldLabel>
                        <Input
                            id="group-cpu-limit"
                            aria-invalid={
                                form.formState.errors.resources?.cpuLimit ? "true" : "false"
                            }
                            {...form.register("resources.cpuLimit")}
                        />
                        <FieldDescription>Maximum CPU the container can consume.</FieldDescription>
                        <FieldError errors={[form.formState.errors.resources?.cpuLimit]} />
                    </Field>
                </FieldGroup>
            </Section>
        </div>
    );
};

export default GroupConfigurationForm;
