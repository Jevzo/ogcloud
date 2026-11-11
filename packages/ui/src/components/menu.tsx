import type { LucideIcon } from "lucide-react";
import { DropdownMenu } from "radix-ui";
import type { ReactElement } from "react";
import { cn } from "../lib/cn.ts";
import { popoverSurface } from "./popover.tsx";

export interface MenuItem {
  label: string;
  icon?: LucideIcon;
  onSelect: () => void;
  danger?: boolean;
  disabled?: boolean;
}

interface MenuProps {
  trigger: ReactElement;
  /** Groups are separated by a line; destructive items belong in the last one. */
  groups: readonly (readonly MenuItem[])[];
  align?: "start" | "end";
}

export function Menu({ trigger, groups, align = "end" }: MenuProps) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>{trigger}</DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align={align}
          sideOffset={6}
          className={cn(popoverSurface, "min-w-48 p-1")}
        >
          {groups.map((items, index) => (
            <DropdownMenu.Group key={items.map((item) => item.label).join("|")}>
              {index > 0 ? (
                <DropdownMenu.Separator className="my-1 h-px bg-border-subtle" />
              ) : null}
              {items.map(
                ({
                  label,
                  icon: Icon,
                  onSelect,
                  danger = false,
                  disabled = false,
                }) => (
                  <DropdownMenu.Item
                    key={label}
                    disabled={disabled}
                    onSelect={onSelect}
                    className={cn(
                      "flex h-row cursor-pointer select-none items-center gap-2.5 rounded-[calc(var(--radius-popover)-8px)] px-2 outline-none data-disabled:cursor-not-allowed data-disabled:opacity-45 data-highlighted:bg-surface-3",
                      danger &&
                        "text-danger-text data-highlighted:bg-danger-soft",
                    )}
                  >
                    {Icon ? (
                      <Icon aria-hidden="true" size={15} strokeWidth={1.75} />
                    ) : null}
                    {label}
                  </DropdownMenu.Item>
                ),
              )}
            </DropdownMenu.Group>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
