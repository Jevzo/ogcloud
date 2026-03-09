import AppNumberInput from "@/components/AppNumberInput";
import FieldHintLabel from "@/components/FieldHintLabel";
import type { PermissionGroupFormValues } from "@/types/permission";

interface PermissionGroupFormFieldsProps {
    values: PermissionGroupFormValues;
    onFieldChange: (
        field: Exclude<keyof PermissionGroupFormValues, "display" | "default">,
        value: string,
    ) => void;
    onDisplayChange: (field: keyof PermissionGroupFormValues["display"], value: string) => void;
    onDefaultChange: (value: boolean) => void;
    disableIdentityFields?: boolean;
}

const DISPLAY_INPUT_CLASS_NAME =
    "app-input-field block w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10";

const PermissionGroupFormFields = ({
    values,
    onFieldChange,
    onDisplayChange,
    onDefaultChange,
    disableIdentityFields = false,
}: PermissionGroupFormFieldsProps) => (
    <div className="space-y-6">
        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
            <h4 className="text-sm font-semibold text-slate-200">Core Settings</h4>

            <div className="mt-4 grid grid-cols-1 gap-5 md:grid-cols-2">
                <label className="app-field-stack">
                    <FieldHintLabel
                        label="Group ID"
                        hint="Unique identifier used in permissions and assignments."
                    />
                    <input
                        type="text"
                        value={values.id}
                        onChange={(event) => onFieldChange("id", event.target.value)}
                        disabled={disableIdentityFields}
                        className={`${DISPLAY_INPUT_CLASS_NAME} disabled:cursor-not-allowed`}
                    />
                </label>

                <label className="app-field-stack">
                    <FieldHintLabel
                        label="Display Name"
                        hint="Friendly name shown in dashboards and tools."
                    />
                    <input
                        type="text"
                        value={values.name}
                        onChange={(event) => onFieldChange("name", event.target.value)}
                        className={DISPLAY_INPUT_CLASS_NAME}
                    />
                </label>

                <label className="app-field-stack">
                    <FieldHintLabel
                        label="Weight"
                        hint="Higher weight usually indicates stronger rank precedence."
                    />
                    <AppNumberInput
                        value={values.weight}
                        onChangeValue={(value) => onFieldChange("weight", value)}
                        step={1}
                    />
                </label>

                <div className="app-field-stack">
                    <FieldHintLabel
                        label="Default Group"
                        hint="Fallback group for players without explicit assignments."
                    />
                    <button
                        type="button"
                        onClick={() => onDefaultChange(!values.default)}
                        className="app-input-field group flex w-full items-center justify-between px-3 text-left"
                        aria-pressed={values.default}
                        aria-label={`Default group ${values.default ? "enabled" : "disabled"}`}
                    >
                        <span
                            className={`text-sm font-medium ${
                                values.default ? "text-slate-100" : "text-slate-300"
                            }`}
                        >
                            {values.default ? "Enabled" : "Disabled"}
                        </span>
                        <span
                            className={`relative inline-flex h-5 w-9 shrink-0 rounded-full border transition-all duration-500 ease-in-out ${
                                values.default
                                    ? "border-primary/40 bg-primary/85 group-hover:bg-secondary"
                                    : "border-slate-600 bg-slate-700/70 group-hover:bg-slate-700"
                            }`}
                        >
                            <span
                                className={`absolute top-1/2 left-0.5 h-4 w-4 rounded-full border border-white/70 bg-white shadow-[0_2px_8px_rgba(15,23,42,0.3)] transition-transform duration-500 ease-in-out ${
                                    values.default
                                        ? "translate-x-4 -translate-y-1/2"
                                        : "translate-x-0 -translate-y-1/2"
                                }`}
                            />
                        </span>
                    </button>
                </div>
            </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-950/30 p-5">
            <h4 className="text-sm font-semibold text-slate-200">Display Formatting</h4>

            <div className="mt-4 grid grid-cols-1 gap-5 md:grid-cols-2">
                <label className="app-field-stack">
                    <FieldHintLabel
                        label="Chat Prefix"
                        hint="Text shown before player names in chat."
                    />
                    <input
                        type="text"
                        value={values.display.chatPrefix}
                        onChange={(event) => onDisplayChange("chatPrefix", event.target.value)}
                        className={DISPLAY_INPUT_CLASS_NAME}
                    />
                </label>

                <label className="app-field-stack">
                    <FieldHintLabel
                        label="Chat Suffix"
                        hint="Text appended after player names in chat."
                    />
                    <input
                        type="text"
                        value={values.display.chatSuffix}
                        onChange={(event) => onDisplayChange("chatSuffix", event.target.value)}
                        className={DISPLAY_INPUT_CLASS_NAME}
                    />
                </label>

                <label className="app-field-stack">
                    <FieldHintLabel label="Name Color" hint="Color code applied to player names." />
                    <input
                        type="text"
                        value={values.display.nameColor}
                        onChange={(event) => onDisplayChange("nameColor", event.target.value)}
                        className={DISPLAY_INPUT_CLASS_NAME}
                    />
                </label>

                <label className="app-field-stack">
                    <FieldHintLabel label="Tab Prefix" hint="Prefix displayed in the tab list." />
                    <input
                        type="text"
                        value={values.display.tabPrefix}
                        onChange={(event) => onDisplayChange("tabPrefix", event.target.value)}
                        className={DISPLAY_INPUT_CLASS_NAME}
                    />
                </label>
            </div>
        </div>
    </div>
);

export default PermissionGroupFormFields;
