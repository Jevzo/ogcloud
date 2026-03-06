import AppNumberInput from "@/components/AppNumberInput";
import AppSelect from "@/components/AppSelect";
import type { GroupFormValues } from "@/types/group";
import type { TemplateRecord } from "@/types/template";

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
            <label className="app-field-label">
              Group Name
            </label>
            <input
              type="text"
              value={values.id}
              onChange={(event) => onFieldChange("id", event.target.value)}
              disabled={disableIdentityFields}
              className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 disabled:cursor-not-allowed"
            />
          </div>
          <AppSelect
            label="Group Type"
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
      )}

      <div className={SECTION_CLASS_NAME}>
        <h4 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
          Template & Runtime
        </h4>
        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
          <div className="app-field-stack">
            <label className="app-field-label">
              Template Bucket
            </label>
            <input
              type="text"
              value={values.templateBucket}
              onChange={(event) => onFieldChange("templateBucket", event.target.value)}
              className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
          <AppSelect
            label="Template"
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
          <AppSelect
            label="Server Image"
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
          <div className="app-field-stack">
            <label className="app-field-label">
              Drain Timeout (Seconds)
            </label>
            <AppNumberInput
              value={values.drainTimeoutSeconds}
              min={1}
              step={1}
              onChangeValue={(value) => onFieldChange("drainTimeoutSeconds", value)}
            />
          </div>
          {isStaticGroup && (
            <div className="app-field-stack">
              <label className="app-field-label">
                Storage Size
              </label>
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
          <label className="app-field-label">
            JVM Flags
          </label>
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
            <label className="app-field-label">
              Min Online
            </label>
            <AppNumberInput
              value={values.scaling.minOnline}
              min={0}
              step={1}
              onChangeValue={(value) => onScalingChange("minOnline", value)}
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              Max Instances
            </label>
            <AppNumberInput
              value={values.scaling.maxInstances}
              min={1}
              step={1}
              onChangeValue={(value) => onScalingChange("maxInstances", value)}
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              Players Per Server
            </label>
            <AppNumberInput
              value={values.scaling.playersPerServer}
              min={1}
              step={1}
              onChangeValue={(value) => onScalingChange("playersPerServer", value)}
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              Scale Up Threshold
            </label>
            <AppNumberInput
              value={values.scaling.scaleUpThreshold}
              step={0.01}
              onChangeValue={(value) => onScalingChange("scaleUpThreshold", value)}
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              Scale Down Threshold
            </label>
            <AppNumberInput
              value={values.scaling.scaleDownThreshold}
              step={0.01}
              onChangeValue={(value) => onScalingChange("scaleDownThreshold", value)}
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              Cooldown (Seconds)
            </label>
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
            <label className="app-field-label">
              Memory Request
            </label>
            <input
              type="text"
              value={values.resources.memoryRequest}
              onChange={(event) => onResourceChange("memoryRequest", event.target.value)}
              className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              Memory Limit
            </label>
            <input
              type="text"
              value={values.resources.memoryLimit}
              onChange={(event) => onResourceChange("memoryLimit", event.target.value)}
              className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              CPU Request
            </label>
            <input
              type="text"
              value={values.resources.cpuRequest}
              onChange={(event) => onResourceChange("cpuRequest", event.target.value)}
              className="app-input-field block w-full rounded-lg border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            />
          </div>
          <div className="app-field-stack">
            <label className="app-field-label">
              CPU Limit
            </label>
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
