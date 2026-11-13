import { Check, type LucideIcon } from "lucide-react";
import { DropdownMenu } from "radix-ui";
import type { ReactElement, ReactNode } from "react";
import { cn } from "../lib/cn.ts";
import { popoverSurface } from "./popover.tsx";

export interface MenuItem {
  label: string;
  icon?: LucideIcon;
  onSelect: () => void;
  danger?: boolean;
  disabled?: boolean;
}

/** A choice of one, such as the theme; it stays open so the effect shows at once. */
export interface MenuChoice<T extends string = string> {
  label: string;
  value: T;
  options: readonly { value: T; label: string }[];
  onValueChange: (value: T) => void;
}

export type MenuGroup = readonly MenuItem[] | MenuChoice;

interface MenuProps {
  trigger: ReactElement;
  /** Groups are separated by a line; destructive items belong in the last one. */
  groups: readonly MenuGroup[];
  align?: "start" | "end";
  side?: "top" | "right" | "bottom" | "left";
  /** Shown above the groups, e.g. who is signed in. */
  header?: ReactNode;
}

const itemLook =
  "flex h-row cursor-pointer select-none items-center gap-2.5 rounded-[calc(var(--radius-popover)-8px)] px-2 outline-none data-disabled:cursor-not-allowed data-disabled:opacity-45 data-highlighted:bg-surface-3";

function isChoice(group: MenuGroup): group is MenuChoice {
  return !Array.isArray(group);
}

function groupKey(group: MenuGroup): string {
  return isChoice(group)
    ? group.label
    : group.map((item) => item.label).join("|");
}

export function Menu({
  trigger,
  groups,
  align = "end",
  side = "bottom",
  header,
}: MenuProps) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>{trigger}</DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align={align}
          side={side}
          sideOffset={6}
          collisionPadding={8}
          className={cn(popoverSurface, "min-w-52 p-1")}
        >
          {header === undefined ? null : (
            <div className="px-2 pt-1.5 pb-2">{header}</div>
          )}
          {groups.map((group, index) => (
            <DropdownMenu.Group key={groupKey(group)}>
              {index > 0 || header !== undefined ? (
                <DropdownMenu.Separator className="my-1 h-px bg-border-subtle" />
              ) : null}
              {isChoice(group) ? (
                <>
                  <DropdownMenu.Label className="px-2 pt-1 pb-0.5 font-label text-meta text-text-3">
                    {group.label}
                  </DropdownMenu.Label>
                  <DropdownMenu.RadioGroup
                    value={group.value}
                    onValueChange={group.onValueChange}
                  >
                    {group.options.map((option) => (
                      <DropdownMenu.RadioItem
                        key={option.value}
                        value={option.value}
                        onSelect={(event) => event.preventDefault()}
                        className={itemLook}
                      >
                        <span className="flex-1">{option.label}</span>
                        <DropdownMenu.ItemIndicator>
                          <Check
                            aria-hidden="true"
                            size={15}
                            strokeWidth={2.25}
                            className="text-accent"
                          />
                        </DropdownMenu.ItemIndicator>
                      </DropdownMenu.RadioItem>
                    ))}
                  </DropdownMenu.RadioGroup>
                </>
              ) : (
                group.map(
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
                        itemLook,
                        danger && "text-danger data-highlighted:bg-danger-soft",
                      )}
                    >
                      {Icon ? (
                        <Icon aria-hidden="true" size={15} strokeWidth={1.75} />
                      ) : null}
                      {label}
                    </DropdownMenu.Item>
                  ),
                )
              )}
            </DropdownMenu.Group>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
