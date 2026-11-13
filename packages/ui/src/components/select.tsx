import { Check, ChevronDown } from "lucide-react";
import { Select as Primitive } from "radix-ui";
import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";
import { useNonce } from "../nonce.ts";
import { controlFrame } from "./input.tsx";
import { popoverSurface } from "./popover.tsx";

export interface SelectOption<T extends string> {
  value: T;
  label: string;
  /** Muted text after the label, e.g. a group's type. */
  hint?: ReactNode;
}

interface SelectProps<T extends string> {
  id?: string;
  options: readonly SelectOption<T>[];
  value: T | undefined;
  onValueChange: (value: T) => void;
  placeholder?: string | undefined;
  disabled?: boolean;
  className?: string;
  "aria-describedby"?: string | undefined;
  "aria-invalid"?: true | undefined;
}

/** A closed list of a few options; longer or searchable lists use Combobox. */
export function Select<T extends string>({
  options,
  value,
  onValueChange,
  placeholder,
  className,
  ...trigger
}: SelectProps<T>) {
  // Radix hides the viewport's scrollbar with a <style> of its own, which the CSP admits by nonce (D-0219).
  const nonce = useNonce();
  return (
    <Primitive.Root
      value={value}
      onValueChange={(next) => onValueChange(next as T)}
    >
      <Primitive.Trigger
        data-skeleton=""
        {...trigger}
        className={cn(
          controlFrame,
          "flex h-control w-full cursor-pointer items-center gap-2 px-2.5 text-left outline-none focus-visible:border-accent focus-visible:ring-3 focus-visible:ring-accent/30 disabled:cursor-not-allowed disabled:opacity-45 data-placeholder:text-text-3 data-[state=open]:border-accent data-[state=open]:ring-3 data-[state=open]:ring-accent/30",
          className,
        )}
      >
        <span className="flex-1 truncate">
          <Primitive.Value placeholder={placeholder} />
        </span>
        <Primitive.Icon>
          <ChevronDown aria-hidden="true" size={16} className="text-text-2" />
        </Primitive.Icon>
      </Primitive.Trigger>
      <Primitive.Portal>
        <Primitive.Content
          position="popper"
          sideOffset={6}
          className={cn(
            popoverSurface,
            "max-h-(--radix-select-content-available-height) min-w-(--radix-select-trigger-width) p-1",
          )}
        >
          <Primitive.Viewport nonce={nonce}>
            {options.map((option) => (
              <Primitive.Item
                key={option.value}
                value={option.value}
                className="flex h-row cursor-pointer select-none items-center gap-2 rounded-[calc(var(--radius-popover)-8px)] px-2 outline-none data-highlighted:bg-surface-3 data-[state=checked]:font-label data-[state=checked]:text-accent"
              >
                <Primitive.ItemText>{option.label}</Primitive.ItemText>
                <span className="flex-1 text-right text-meta text-text-3">
                  {option.hint}
                </span>
                <span className="grid w-4 place-items-center">
                  <Primitive.ItemIndicator>
                    <Check aria-hidden="true" size={15} strokeWidth={2.25} />
                  </Primitive.ItemIndicator>
                </span>
              </Primitive.Item>
            ))}
          </Primitive.Viewport>
        </Primitive.Content>
      </Primitive.Portal>
    </Primitive.Root>
  );
}
