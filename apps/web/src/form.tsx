import type { TextPreviewRequestField } from "@ogcloud/api-client";
import {
  Field,
  Input,
  Select,
  type SelectOption,
  Switch,
  TagTextField,
} from "@ogcloud/ui";
import {
  createFormHook,
  createFormHookContexts,
  type StandardSchemaV1,
  useStore,
} from "@tanstack/react-form";
import type { TFunction } from "i18next";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { textProblem, useTextPreview } from "./texts.ts";

// The dashboard's forms (D-0211): TanStack Form with the contract's zod schemas, drawn with the UI kit's fields.

const { fieldContext, formContext, useFieldContext } = createFormHookContexts();

/** The field's first problem, once it was touched or the form was submitted. */
function useFieldError(): string | undefined {
  const field = useFieldContext<unknown>();
  const errors = useStore(field.store, (state) => state.meta.errors);
  const touched = useStore(field.store, (state) => state.meta.isTouched);
  const submitted = useStore(
    field.form.store,
    (state) => state.submissionAttempts > 0,
  );
  if (!touched && !submitted) return undefined;
  const first: unknown = errors[0];
  if (typeof first === "string") return first;
  if (typeof first === "object" && first !== null && "message" in first) {
    return String(first.message);
  }
  return undefined;
}

interface TextFieldProps {
  label: string;
  hint?: string;
  mono?: boolean;
  placeholder?: string;
  suffix?: ReactNode;
  /** Empty means none: the value becomes null, e.g. no CPU limit. */
  optional?: boolean;
}

function TextField({
  label,
  hint,
  mono = false,
  placeholder,
  suffix,
  optional = false,
}: TextFieldProps) {
  const field = useFieldContext<string | null | undefined>();
  return (
    <Field label={label} hint={hint} error={useFieldError()}>
      {(control) => (
        <Input
          {...control}
          mono={mono}
          placeholder={placeholder}
          suffix={suffix}
          value={field.state.value ?? ""}
          onBlur={field.handleBlur}
          onChange={(event) =>
            field.handleChange(
              optional && event.target.value === "" ? null : event.target.value,
            )
          }
        />
      )}
    </Field>
  );
}

interface NumberFieldProps {
  label: string;
  hint?: string;
  suffix?: ReactNode;
  /** Shown multiplied by this, e.g. 100 for a fraction edited as a percentage. */
  scale?: number;
}

// Keeps a scaled fraction such as 0.8 → 80 free of floating-point tails.
const SCALED_DIGITS = 1_000;

function NumberField({ label, hint, suffix, scale = 1 }: NumberFieldProps) {
  const field = useFieldContext<number>();
  const value = field.state.value;
  return (
    <Field label={label} hint={hint} error={useFieldError()}>
      {(control) => (
        <Input
          {...control}
          type="number"
          inputMode="decimal"
          suffix={suffix}
          value={
            Number.isNaN(value)
              ? ""
              : Math.round(value * scale * SCALED_DIGITS) / SCALED_DIGITS
          }
          onBlur={field.handleBlur}
          onChange={(event) =>
            field.handleChange(event.target.valueAsNumber / scale)
          }
        />
      )}
    </Field>
  );
}

interface SelectFieldProps {
  label: string;
  hint?: string;
  options: readonly SelectOption<string>[];
  placeholder?: string;
  disabled?: boolean;
}

function SelectField({
  label,
  hint,
  options,
  placeholder,
  disabled = false,
}: SelectFieldProps) {
  const field = useFieldContext<string>();
  return (
    <Field label={label} hint={hint} error={useFieldError()}>
      {(control) => (
        <Select
          {...control}
          options={options}
          placeholder={placeholder}
          disabled={disabled}
          value={field.state.value === "" ? undefined : field.state.value}
          onValueChange={field.handleChange}
        />
      )}
    </Field>
  );
}

interface SwitchFieldProps {
  label: string;
  description?: string;
  disabled?: boolean;
}

/** An on-or-off setting with its label and what it does. */
function SwitchField({
  label,
  description,
  disabled = false,
}: SwitchFieldProps) {
  const field = useFieldContext<boolean>();
  return (
    <Switch
      label={label}
      description={description}
      disabled={disabled}
      checked={field.state.value}
      onCheckedChange={field.handleChange}
    />
  );
}

interface TagTextFormFieldProps {
  label: string;
  hint?: string;
  /** Which text it is, which decides its placeholders and checks (D-0200). */
  field: TextPreviewRequestField;
  multiline?: boolean;
}

const HEX_COLOUR = /#[0-9a-fA-F]{6}/g;

/** An in-game text with its tags highlighted and the API's problems marked where they are. */
function TagTextFormField({
  label,
  hint,
  field: textField,
  multiline = false,
}: TagTextFormFieldProps) {
  const { t } = useTranslation("web");
  const field = useFieldContext<string>();
  const preview = useTextPreview(textField, field.state.value);
  const problems = preview.data?.errors ?? [];
  const first = problems[0];
  return (
    <Field
      label={label}
      hint={hint}
      error={
        first === undefined
          ? undefined
          : problems.length > 1
            ? t("texts.moreProblems", {
                problem: textProblem(t, first),
                count: problems.length - 1,
              })
            : textProblem(t, first)
      }
    >
      {(control) => (
        <TagTextField
          {...control}
          value={field.state.value}
          onChange={field.handleChange}
          problems={problems}
          placeholders={preview.data?.placeholders ?? []}
          recentColours={[
            ...new Set(field.state.value.match(HEX_COLOUR) ?? []),
          ]}
          multiline={multiline}
        />
      )}
    </Field>
  );
}

/**
 * A contract zod schema as the validator of a form typed by the contract's TypeScript model. orval's two outputs
 * differ only in that zod's optional nulls also admit `undefined`, which the form never holds.
 */
export function contractSchema<T>(
  schema: StandardSchemaV1,
): StandardSchemaV1<T> {
  return schema as StandardSchemaV1<T>;
}

export const { useAppForm, withFieldGroup } = createFormHook({
  fieldContext,
  formContext,
  fieldComponents: {
    TextField,
    NumberField,
    SelectField,
    SwitchField,
    TagTextFormField,
  },
  formComponents: {},
});

/** How many leaf values differ between two form states, for the save bar's count (D-0210). */
export function countChanges(before: unknown, after: unknown): number {
  if (
    typeof before === "object" &&
    before !== null &&
    typeof after === "object" &&
    after !== null
  ) {
    const keys = new Set([...Object.keys(before), ...Object.keys(after)]);
    return [...keys].reduce(
      (sum, key) =>
        sum + countChanges(Reflect.get(before, key), Reflect.get(after, key)),
      0,
    );
  }
  return Object.is(before, after) ? 0 : 1;
}

/** Puts zod's messages for the contract's schemas into the catalog's words (D-0050); called once at start. */
export function localizeValidation(t: TFunction<"web">): void {
  z.config({
    customError: (issue) => {
      if (issue.code === "invalid_type") {
        return issue.expected === "number"
          ? t("forms.number")
          : t("forms.required");
      }
      return t("forms.invalid");
    },
  });
}

interface SettingsFormOptions<V, S extends object, A> {
  view: V;
  settingsOf: (view: V) => S;
  schema: StandardSchemaV1<{ settings: S }>;
  save: (settings: S, base: V) => Promise<A>;
  onSaved: (answer: A) => void;
  onFailed: (error: unknown) => void;
}

/**
 * An in-place editor for a resource's settings: the form, how many values changed and whether it is saving. Edits start
 * from `view`; a newer view replaces it only while nothing is edited, or right after a save, so a refetch never drops
 * edits. `save` writes against `base`, the view the edits started from, so a stale save is refused (D-0069, D-0210).
 */
export function useSettingsForm<V, S extends object, A>({
  view,
  settingsOf,
  schema,
  save,
  onSaved,
  onFailed,
}: SettingsFormOptions<V, S, A>) {
  const [base, setBase] = useState(view);
  const adoptNext = useRef(false);
  const form = useAppForm({
    defaultValues: { settings: settingsOf(base) },
    validators: { onChange: schema },
    onSubmit: async ({ value }) => {
      try {
        const answer = await save(value.settings, base);
        adoptNext.current = true;
        onSaved(answer);
      } catch (error) {
        onFailed(error);
      }
    },
  });
  const changes = useStore(form.store, (state) =>
    countChanges(settingsOf(base), state.values.settings),
  );
  const saving = useStore(form.store, (state) => state.isSubmitting);
  useEffect(() => {
    if (view === base || (changes > 0 && !adoptNext.current)) return;
    adoptNext.current = false;
    setBase(view);
    form.reset({ settings: settingsOf(view) });
  }, [view, base, changes, form, settingsOf]);
  return { form, changes, saving, base };
}

const settingsField = <S,>(view: { settings: S }): S => view.settings;

/** {@link useSettingsForm} for a view that holds its settings as `settings`, as groups, NPCs and the network do. */
export function useViewSettingsForm<
  V extends { settings: S },
  S extends object,
  A,
>(options: Omit<SettingsFormOptions<V, S, A>, "settingsOf">) {
  return useSettingsForm<V, S, A>({ ...options, settingsOf: settingsField });
}

/**
 * The body of a settings save whose maintenance flag has an action of its own: the flag stays as the edits' base
 * had it, so a save never switches maintenance (groups, the network).
 */
export function keepingMaintenance<S extends { maintenance: boolean }>(
  settings: S,
  base: { settings: S; version: number },
): { settings: S; version: number } {
  return {
    settings: { ...settings, maintenance: base.settings.maintenance },
    version: base.version,
  };
}
