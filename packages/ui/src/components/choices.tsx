import { Check } from "lucide-react";
import {
  Checkbox as CheckboxPrimitive,
  RadioGroup as RadioPrimitive,
  Switch as SwitchPrimitive,
} from "radix-ui";
import type { ComponentProps, ReactNode } from "react";
import { useId } from "react";
import { cn } from "../lib/cn.ts";

interface LabelledProps {
  label: ReactNode;
  description?: ReactNode;
}

function Labelled({ id, label, description }: LabelledProps & { id: string }) {
  return (
    <span className="flex flex-col">
      <label htmlFor={id} className="cursor-pointer font-label">
        {label}
      </label>
      {description === undefined ? null : (
        <span className="text-small text-text-2">{description}</span>
      )}
    </span>
  );
}

export function Switch({
  label,
  description,
  className,
  ...props
}: LabelledProps &
  Omit<ComponentProps<typeof SwitchPrimitive.Root>, "children">) {
  const id = useId();
  return (
    <div className={cn("flex items-start gap-3", className)}>
      <SwitchPrimitive.Root
        id={id}
        data-skeleton=""
        {...props}
        className="relative mt-0.5 h-5 w-9 shrink-0 cursor-pointer rounded-full bg-surface-3 shadow-[inset_0_0_0_1px_var(--og-border-control)] disabled:cursor-not-allowed disabled:opacity-45 data-[state=checked]:bg-accent data-[state=checked]:shadow-none"
      >
        <SwitchPrimitive.Thumb className="block size-4 translate-x-0.5 rounded-full bg-on-accent shadow-raised transition-transform data-[state=checked]:translate-x-4.5" />
      </SwitchPrimitive.Root>
      <Labelled id={id} label={label} description={description} />
    </div>
  );
}

export function Checkbox({
  label,
  description,
  className,
  ...props
}: LabelledProps &
  Omit<ComponentProps<typeof CheckboxPrimitive.Root>, "children">) {
  const id = useId();
  return (
    <div className={cn("flex items-start gap-2.5", className)}>
      <CheckboxPrimitive.Root
        data-skeleton=""
        id={id}
        {...props}
        className="mt-0.5 grid size-4 shrink-0 cursor-pointer place-items-center rounded-small border border-border-control bg-surface disabled:cursor-not-allowed disabled:opacity-45 data-[state=checked]:border-accent data-[state=checked]:bg-accent"
      >
        <CheckboxPrimitive.Indicator>
          <Check
            aria-hidden="true"
            size={12}
            strokeWidth={3}
            className="text-on-accent"
          />
        </CheckboxPrimitive.Indicator>
      </CheckboxPrimitive.Root>
      <Labelled id={id} label={label} description={description} />
    </div>
  );
}

export interface RadioOption<T extends string> {
  value: T;
  label: ReactNode;
  description?: ReactNode;
}

interface RadioGroupProps<T extends string> {
  legend: ReactNode;
  options: readonly RadioOption<T>[];
  value: T;
  onValueChange: (value: T) => void;
  className?: string;
}

export function RadioGroup<T extends string>({
  legend,
  options,
  value,
  onValueChange,
  className,
}: RadioGroupProps<T>) {
  const id = useId();
  return (
    <fieldset className={cn("flex flex-col gap-2", className)}>
      <legend className="mb-2 font-label">{legend}</legend>
      <RadioPrimitive.Root
        value={value}
        onValueChange={(next) => onValueChange(next as T)}
        className="flex flex-col gap-2"
      >
        {options.map((option) => (
          <div key={option.value} className="flex items-start gap-2.5">
            <RadioPrimitive.Item
              id={`${id}-${option.value}`}
              value={option.value}
              className="mt-0.5 size-4 shrink-0 cursor-pointer rounded-full border border-border-control bg-surface data-[state=checked]:border-5 data-[state=checked]:border-accent"
            />
            <span className="flex flex-col">
              <label
                htmlFor={`${id}-${option.value}`}
                className="cursor-pointer"
              >
                {option.label}
              </label>
              {option.description === undefined ? null : (
                <span className="text-meta text-text-3">
                  {option.description}
                </span>
              )}
            </span>
          </div>
        ))}
      </RadioPrimitive.Root>
    </fieldset>
  );
}
