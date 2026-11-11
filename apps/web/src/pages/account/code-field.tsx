import { Field, Input } from "@ogcloud/ui";

/** A six-digit code from an authenticator app. */
export function CodeField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (code: string) => void;
}) {
  return (
    <Field label={label}>
      {(control) => (
        <Input
          {...control}
          mono
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={CODE_LENGTH}
          value={value}
          onChange={(event) => onChange(event.target.value.replace(/\D/g, ""))}
        />
      )}
    </Field>
  );
}

export const CODE_LENGTH = 6;
