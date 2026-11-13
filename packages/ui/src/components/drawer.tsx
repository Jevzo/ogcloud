import { X } from "lucide-react";
import { Dialog as Primitive } from "radix-ui";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "./button.tsx";

interface DrawerProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  /** Badges beside the title, such as a server's state. */
  badges?: ReactNode;
  children: ReactNode;
  /** The record's actions, right-aligned with the primary one last (D-0210). */
  footer?: ReactNode;
}

/** A record's details beside its list, sliding in from the right; the list stays in view (D-0231). */
export function Drawer({
  open,
  onOpenChange,
  title,
  badges,
  children,
  footer,
}: DrawerProps) {
  const { t } = useTranslation("ui");
  return (
    <Primitive.Root open={open} onOpenChange={onOpenChange}>
      <Primitive.Portal>
        <Primitive.Overlay className="fixed inset-0 z-40 bg-scrim data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
        <Primitive.Content
          aria-describedby={undefined}
          // Focus lands on the drawer itself, so its close button's tooltip does not pop up on every opening.
          onOpenAutoFocus={(event) => {
            event.preventDefault();
            if (event.currentTarget instanceof HTMLElement) {
              event.currentTarget.focus();
            }
          }}
          className="fixed inset-y-0 right-0 z-50 flex w-[min(var(--container-drawer),100vw)] flex-col border-border border-l bg-floating shadow-overlay outline-none data-[state=closed]:animate-drawer-out data-[state=open]:animate-drawer-in"
        >
          <div className="flex min-h-header items-center gap-3 border-border-subtle border-b px-5">
            <Primitive.Title className="min-w-0 truncate text-heading">
              {title}
            </Primitive.Title>
            {badges}
            <span className="flex-1" />
            <Primitive.Close asChild>
              <Button kind="ghost" size="sm" icon={X} iconOnly>
                {t("actions.close")}
              </Button>
            </Primitive.Close>
          </div>
          <div className="flex min-h-0 flex-1 flex-col gap-5 overflow-y-auto p-5">
            {children}
          </div>
          {footer === undefined ? null : (
            <div className="flex justify-end gap-2 border-border-subtle border-t px-5 py-3">
              {footer}
            </div>
          )}
        </Primitive.Content>
      </Primitive.Portal>
    </Primitive.Root>
  );
}
