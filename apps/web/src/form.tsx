import { Field, Input, Select, type SelectOption } from "@ogcloud/ui";
import {
  createFormHook,
  createFormHookContexts,
  type StandardSchemaV1,
  useStore,
} from "@tanstack/react-form";
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { z } from "zod";

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
}

function SelectField({ label, hint, options, placeholder }: SelectFieldProps) {
  const field = useFieldContext<string>();
  return (
    <Field label={label} hint={hint} error={useFieldError()}>
      {(control) => (
        <Select
          {...control}
          options={options}
          placeholder={placeholder}
          value={field.state.value === "" ? undefined : field.state.value}
          onValueChange={field.handleChange}
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
  fieldComponents: { TextField, NumberField, SelectField },
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
