import { Tooltip as Primitive } from "radix-ui";
import type { ReactElement, ReactNode } from "react";

interface TooltipProps {
  content: ReactNode;
  children: ReactElement;
  side?: "top" | "right" | "bottom" | "left";
}

/** A short label on hover and keyboard focus; every icon-only button carries one (D-0210). */
export function Tooltip({ content, children, side = "top" }: TooltipProps) {
  return (
    <Primitive.Root>
      <Primitive.Trigger asChild>{children}</Primitive.Trigger>
      <Primitive.Portal>
        <Primitive.Content
          side={side}
          sideOffset={6}
          className="z-50 max-w-72 rounded-control bg-text px-2 py-1 font-label text-meta text-surface shadow-popover data-[state=closed]:animate-pop-out data-[state=delayed-open]:animate-pop-in"
        >
          {content}
        </Primitive.Content>
      </Primitive.Portal>
    </Primitive.Root>
  );
}
