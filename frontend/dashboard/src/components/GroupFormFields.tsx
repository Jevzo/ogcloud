import AppNumberInput from "@/components/AppNumberInput";
import AppSelect from "@/components/AppSelect";
import FieldHintLabel from "@/components/FieldHintLabel";
import type {GroupFormValues} from "@/types/group";
import type {TemplateRecord} from "@/types/template";

const SERVER_IMAGE_OPTIONS = [
    "ogwarsdev/paper:latest",
    "ogwarsdev/velocity:latest",
] as const;
const GROUP_TYPE_OPTIONS = ["DYNAMIC", "STATIC", "PROXY"] as const;
const SECTION_CLASS_NAME = "rounded-xl border border-slate-800 bg-slate-800/30 p-5";

const getTemplateOptionValue = (template: TemplateRecord) =>
    `${template.group}::${template.version}`;

const getTemplateOptionLabel = (template: TemplateRecord) =>
    `${template.group} / ${template.version}`;

interface FieldLabelProps {
    label: string;
    hint: string;
}

const FieldLabel = ({label, hint}: FieldLabelProps) => (
    <FieldHintLabel label={label} hint={hint}/>
);

interface GroupFormFieldsProps {
    values: GroupFormValues;
    onFieldChange: (
        field: Exclude<
            keyof GroupFormValues,
            "scaling" | "resources"
        >,
        value: string
    ) => void;
    onScalingChange: (
        field: keyof GroupFormValues["scaling"],
        value: string
    ) => void;
    onResourceChange: (
        field: keyof GroupFormValues["resources"],
        value: string
    ) => void;
    templates: TemplateRecord[];
    onTemplateChange: (templatePath: string) => void;
    showIdentityFields?: boolean;
    disableIdentityFields?: boolean;
}

const GroupFormFields = ({
                             values,
                             onFieldChange,
                             onScalingChange,
                             onResourceChange,
                             templates,
                             onTemplateChange,
                             showIdentityFields = true,
                             disableIdentityFields = false,
                         }: GroupFormFieldsProps) => {
    const isStaticGroup = values.type.toUpperCase() === "STATIC";
    const selectedTemplate = templates.find(
        (template) =>
            template.group === values.templatePath &&
            template.version === values.templateVersion
    );
    const selectedServerImageExists = SERVER_IMAGE_OPTIONS.includes(
        values.serverImage as (typeof SERVER_IMAGE_OPTIONS)[number]
    );

    return (
        <div className="space-y-6">
            {showIdentityFields && (
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Group Name"
                            hint="Unique ID used for routing, deployment, and API references."
                        />
                        <input
                            type="text"
                            value={values.id}
                            onChange={(event) => onFieldChange("id", event.target.value)}
                            disabled={disableIdentityFields}
                            className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed"
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Group Type"
                            hint="DYNAMIC scales automatically, STATIC is persistent, PROXY is a network entrypoint."
                        />
                        <AppSelect
                            value={values.type}
                            onChangeValue={(value) => onFieldChange("type", value)}
                            disabled={disableIdentityFields}
                        >
                            {GROUP_TYPE_OPTIONS.map((option) => (
                                <option key={option} value={option}>
                                    {option}
                                </option>
                            ))}
                        </AppSelect>
                    </div>
                </div>
            )}

            <div className={SECTION_CLASS_NAME}>
                <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                    Template & Runtime
                </h4>
                <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Template Bucket"
                            hint="Object storage bucket containing template archives."
                        />
                        <input
                            type="text"
                            value={values.templateBucket}
                            onChange={(event) => onFieldChange("templateBucket", event.target.value)}
                            className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Template"
                            hint="Template source used when bootstrapping new instances in this group."
                        />
                        <AppSelect
                            value={selectedTemplate ? getTemplateOptionValue(selectedTemplate) : "__custom__"}
                            onChangeValue={(value) => {
                                if (value === "__custom__") {
                                    return;
                                }

                                onTemplateChange(value);
                            }}
                        >
                            {selectedTemplate ? null : (
                                <option value="__custom__">
                                    {values.templateVersion && values.templatePath
                                        ? `Current / ${values.templateVersion}`
                                        : "Select a template"}
                                </option>
                            )}
                            {templates.map((template) => (
                                <option key={template.path} value={getTemplateOptionValue(template)}>
                                    {getTemplateOptionLabel(template)}
                                </option>
                            ))}
                        </AppSelect>
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Server Image"
                            hint="Container image used to run each server pod for this group."
                        />
                        <AppSelect
                            value={selectedServerImageExists ? values.serverImage : "__custom-image__"}
                            onChangeValue={(value) => {
                                if (value === "__custom-image__") {
                                    return;
                                }

                                onFieldChange("serverImage", value);
                            }}
                        >
                            {selectedServerImageExists ? null : (
                                <option value="__custom-image__">
                                    {values.serverImage || "Select a server image"}
                                </option>
                            )}
                            {SERVER_IMAGE_OPTIONS.map((option) => (
                                <option key={option} value={option}>
                                    {option}
                                </option>
                            ))}
                        </AppSelect>
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Drain Timeout (Seconds)"
                            hint="Grace period before forcefully stopping a draining server."
                        />
                        <AppNumberInput
                            value={values.drainTimeoutSeconds}
                            min={1}
                            step={1}
                            onChangeValue={(value) => onFieldChange("drainTimeoutSeconds", value)}
                        />
                    </div>
                    {isStaticGroup && (
                        <div className="app-field-stack">
                            <FieldLabel
                                label="Storage Size"
                                hint="Persistent volume size reserved for STATIC group servers."
                            />
                            <input
                                type="text"
                                value={values.storageSize}
                                onChange={(event) => onFieldChange("storageSize", event.target.value)}
                                className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                            />
                        </div>
                    )}
                </div>
                <div className="app-field-stack mt-4">
                    <FieldLabel
                        label="JVM Flags"
                        hint="JVM startup options passed directly to the Minecraft process."
                    />
                    <input
                        type="text"
                        value={values.jvmFlags}
                        onChange={(event) => onFieldChange("jvmFlags", event.target.value)}
                        className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                    />
                </div>
            </div>

            <div className={SECTION_CLASS_NAME}>
                <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                    Scaling
                </h4>
                <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-3">
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Min Online"
                            hint="Minimum number of instances kept available at all times."
                        />
                        <AppNumberInput
                            value={values.scaling.minOnline}
                            min={0}
                            step={1}
                            onChangeValue={(value) => onScalingChange("minOnline", value)}
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Max Instances"
                            hint="Hard autoscaling cap for simultaneously running instances."
                        />
                        <AppNumberInput
                            value={values.scaling.maxInstances}
                            min={1}
                            step={1}
                            onChangeValue={(value) => onScalingChange("maxInstances", value)}
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Players Per Server"
                            hint="Target player capacity used for balancing and scaling calculations."
                        />
                        <AppNumberInput
                            value={values.scaling.playersPerServer}
                            min={1}
                            step={1}
                            onChangeValue={(value) => onScalingChange("playersPerServer", value)}
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Scale Up Threshold"
                            hint="Utilization level that triggers a scale-up action."
                        />
                        <AppNumberInput
                            value={values.scaling.scaleUpThreshold}
                            step={0.01}
                            onChangeValue={(value) => onScalingChange("scaleUpThreshold", value)}
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Scale Down Threshold"
                            hint="Utilization level below which downscaling can occur."
                        />
                        <AppNumberInput
                            value={values.scaling.scaleDownThreshold}
                            step={0.01}
                            onChangeValue={(value) => onScalingChange("scaleDownThreshold", value)}
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Cooldown (Seconds)"
                            hint="Minimum wait time between consecutive autoscaling operations."
                        />
                        <AppNumberInput
                            value={values.scaling.cooldownSeconds}
                            min={1}
                            step={1}
                            onChangeValue={(value) => onScalingChange("cooldownSeconds", value)}
                        />
                    </div>
                </div>
            </div>

            <div className={SECTION_CLASS_NAME}>
                <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                    Resources
                </h4>
                <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Memory Request"
                            hint="Guaranteed memory reserved for the pod by Kubernetes."
                        />
                        <input
                            type="text"
                            value={values.resources.memoryRequest}
                            onChange={(event) => onResourceChange("memoryRequest", event.target.value)}
                            className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="Memory Limit"
                            hint="Maximum memory available before the container can be OOM-killed."
                        />
                        <input
                            type="text"
                            value={values.resources.memoryLimit}
                            onChange={(event) => onResourceChange("memoryLimit", event.target.value)}
                            className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="CPU Request"
                            hint="Guaranteed CPU reserved for the pod scheduler."
                        />
                        <input
                            type="text"
                            value={values.resources.cpuRequest}
                            onChange={(event) => onResourceChange("cpuRequest", event.target.value)}
                            className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                    </div>
                    <div className="app-field-stack">
                        <FieldLabel
                            label="CPU Limit"
                            hint="Maximum CPU the container can consume."
                        />
                        <input
                            type="text"
                            value={values.resources.cpuLimit}
                            onChange={(event) => onResourceChange("cpuLimit", event.target.value)}
                            className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                        />
                    </div>
                </div>
            </div>
        </div>
    );
};

export default GroupFormFields;
