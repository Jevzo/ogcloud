import { Command } from "cmdk";
import { Check, ChevronDown, X } from "lucide-react";
import { Popover as PopoverPrimitive } from "radix-ui";
import { type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "../lib/cn.ts";
import { controlFrame } from "./input.tsx";
import { Popover, popoverSurface } from "./popover.tsx";

export interface ListOption<T extends string> {
  value: T;
  label: string;
  hint?: ReactNode;
}

interface OptionListProps<T extends string> {
  options: readonly ListOption<T>[];
  isSelected: (value: T) => boolean;
  onPick: (value: T) => void;
  multiple: boolean;
  mono: boolean;
}

function OptionList<T extends string>({
  options,
  isSelected,
  onPick,
  multiple,
  mono,
}: OptionListProps<T>) {
  const { t } = useTranslation("ui");
  return (
    <Command className="flex flex-col" loop>
      <Command.Input
        placeholder={t("list.search")}
        className="h-control border-border-subtle border-b bg-transparent px-3 outline-none placeholder:text-text-3"
      />
      <Command.List className="max-h-72 overflow-y-auto p-1">
        <Command.Empty className="px-2 py-3 text-center text-small text-text-3">
          {t("list.noResults")}
        </Command.Empty>
        {options.map((option) => {
          const selected = isSelected(option.value);
          return (
            <Command.Item
              key={option.value}
              value={option.value}
              keywords={[option.label]}
              onSelect={() => onPick(option.value)}
              className="flex h-row cursor-pointer select-none items-center gap-2.5 rounded-[calc(var(--radius-popover)-8px)] px-2 data-[selected=true]:bg-surface-3"
            >
              {multiple ? (
                <span
                  aria-hidden="true"
                  className={cn(
                    "grid size-4 shrink-0 place-items-center rounded-[4px] border",
                    selected
                      ? "border-accent bg-accent"
                      : "border-border-control bg-surface",
                  )}
                >
                  {selected ? (
                    <Check
                      size={11}
                      strokeWidth={3.2}
                      className="text-on-accent"
                    />
                  ) : null}
                </span>
              ) : null}
              <span
                className={cn(
                  "flex-1 truncate",
                  mono && "font-mono text-mono",
                  !multiple && selected && "font-label text-accent-text",
                )}
              >
                {option.label}
              </span>
              <span className="text-meta text-text-3">{option.hint}</span>
              {multiple ? null : (
                <span className="grid w-4 place-items-center">
                  {selected ? (
                    <Check
                      aria-hidden="true"
                      size={15}
                      strokeWidth={2.25}
                      className="text-accent-text"
                    />
                  ) : null}
                </span>
              )}
            </Command.Item>
          );
        })}
      </Command.List>
    </Command>
  );
}

interface ComboboxProps<T extends string> {
  id?: string;
  options: readonly ListOption<T>[];
  value: T | undefined;
  onValueChange: (value: T) => void;
  placeholder: string;
  mono?: boolean;
  "aria-describedby"?: string | undefined;
  "aria-invalid"?: true | undefined;
}

/** One value from a long or searchable list. */
export function Combobox<T extends string>({
  options,
  value,
  onValueChange,
  placeholder,
  mono = false,
  ...trigger
}: ComboboxProps<T>) {
  const [open, setOpen] = useState(false);
  const current = options.find((option) => option.value === value);
  return (
    <Popover
      open={open}
      onOpenChange={setOpen}
      className="w-(--radix-popover-trigger-width) min-w-64"
      trigger={
        <button
          type="button"
          role="combobox"
          aria-expanded={open}
          {...trigger}
          className={cn(
            controlFrame,
            "flex h-control w-full cursor-pointer items-center gap-2 px-2.5 text-left outline-none focus-visible:border-accent focus-visible:ring-3 focus-visible:ring-accent/30 aria-expanded:border-accent aria-expanded:ring-3 aria-expanded:ring-accent/30",
          )}
        >
          <span
            className={cn(
              "flex-1 truncate",
              current === undefined && "text-text-3",
              mono && "font-mono text-mono",
            )}
          >
            {current?.label ?? placeholder}
          </span>
          <ChevronDown aria-hidden="true" size={16} className="text-text-2" />
        </button>
      }
    >
      <OptionList
        options={options}
        isSelected={(option) => option === value}
        onPick={(option) => {
          onValueChange(option);
          setOpen(false);
        }}
        multiple={false}
        mono={mono}
      />
    </Popover>
  );
}

interface MultiSelectProps<T extends string>
  extends Omit<ComboboxProps<T>, "value" | "onValueChange"> {
  values: readonly T[];
  onValuesChange: (values: T[]) => void;
}

/** Several values from a searchable list, shown as removable chips. */
export function MultiSelect<T extends string>({
  options,
  values,
  onValuesChange,
  placeholder,
  mono = false,
  ...trigger
}: MultiSelectProps<T>) {
  const { t } = useTranslation("ui");
  const [open, setOpen] = useState(false);
  const chosen = new Set(values);
  const toggle = (value: T) =>
    onValuesChange(
      chosen.has(value)
        ? values.filter((v) => v !== value)
        : [...values, value],
    );
  const labelOf = (value: T) =>
    options.find((option) => option.value === value)?.label ?? value;

  // The list opens under the whole field, chips included, not under the small trigger at its end.
  return (
    <PopoverPrimitive.Root open={open} onOpenChange={setOpen}>
      <PopoverPrimitive.Anchor asChild>
        <div
          className={cn(
            controlFrame,
            "flex min-h-control flex-wrap items-center gap-1.5 p-[3px]",
            open && "border-accent ring-3 ring-accent/30",
          )}
        >
          {values.map((value) => (
            <span
              key={value}
              className={cn(
                "inline-flex h-6 items-center gap-0.5 rounded-[calc(var(--radius-control)-3px)] bg-accent-soft pr-0.5 pl-2 font-label text-accent-text text-meta",
                mono && "font-mono",
              )}
            >
              {labelOf(value)}
              <button
                type="button"
                aria-label={t("list.remove", { label: labelOf(value) })}
                onClick={() => toggle(value)}
                className="grid size-5 cursor-pointer place-items-center rounded-[4px] hover:bg-accent/15"
              >
                <X aria-hidden="true" size={12} strokeWidth={2.25} />
              </button>
            </span>
          ))}
          <PopoverPrimitive.Trigger asChild>
            <button
              type="button"
              role="combobox"
              aria-expanded={open}
              {...trigger}
              className="flex h-6 min-w-32 flex-1 cursor-pointer items-center gap-1 px-1.5 text-left text-text-3 outline-none"
            >
              <span className="flex-1 truncate">{placeholder}</span>
              <ChevronDown
                aria-hidden="true"
                size={16}
                className="text-text-2"
              />
            </button>
          </PopoverPrimitive.Trigger>
        </div>
      </PopoverPrimitive.Anchor>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Content
          align="start"
          sideOffset={6}
          collisionPadding={8}
          className={cn(popoverSurface, "w-(--radix-popover-trigger-width)")}
        >
          <OptionList
            options={options}
            isSelected={(value) => chosen.has(value)}
            onPick={toggle}
            multiple
            mono={mono}
          />
        </PopoverPrimitive.Content>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  );
}
