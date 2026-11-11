import { Popover as Primitive } from "radix-ui";
import type { ComponentProps, ReactElement, ReactNode } from "react";
import { cn } from "../lib/cn.ts";

/** The floating surface menus, pickers and lists share. */
export const popoverSurface =
  "z-50 max-h-(--radix-popper-available-height) overflow-y-auto rounded-popover border border-border bg-surface text-text shadow-popover outline-none data-[state=closed]:animate-pop-out data-[state=open]:animate-pop-in";

interface PopoverProps
  extends Omit<ComponentProps<typeof Primitive.Root>, "children"> {
  trigger: ReactElement;
  children: ReactNode;
  align?: "start" | "center" | "end";
  className?: string;
  label?: string;
}

export function Popover({
  trigger,
  children,
  align = "start",
  className,
  label,
  ...root
}: PopoverProps) {
  return (
    <Primitive.Root {...root}>
      <Primitive.Trigger asChild>{trigger}</Primitive.Trigger>
      <Primitive.Portal>
        <Primitive.Content
          align={align}
          sideOffset={6}
          collisionPadding={8}
          aria-label={label}
          className={cn(
            popoverSurface,
            "origin-(--radix-popover-content-transform-origin)",
            className,
          )}
        >
          {children}
        </Primitive.Content>
      </Primitive.Portal>
    </Primitive.Root>
  );
}
