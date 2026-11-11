import { X } from "lucide-react";
import { Toast as Primitive } from "radix-ui";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";

export interface ToastOptions {
  message: string;
  /** One action, typically Undo; its label names it. */
  action?: { label: string; onPress: () => void };
}

interface QueuedToast extends ToastOptions {
  id: number;
}

const ToastContext = createContext<((toast: ToastOptions) => void) | null>(
  null,
);

/** Shows a short message at the bottom right, with at most one action. */
export function useToast(): (toast: ToastOptions) => void {
  const show = useContext(ToastContext);
  if (show === null) {
    throw new Error("useToast needs a UiProvider");
  }
  return show;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const { t } = useTranslation("ui");
  const [toasts, setToasts] = useState<QueuedToast[]>([]);
  const nextId = useRef(0);

  const show = useCallback((toast: ToastOptions) => {
    const id = nextId.current++;
    setToasts((current) => [...current, { ...toast, id }]);
  }, []);
  const dismiss = useCallback(
    (id: number) =>
      setToasts((current) => current.filter((toast) => toast.id !== id)),
    [],
  );

  return (
    <ToastContext.Provider value={show}>
      <Primitive.Provider swipeDirection="right">
        {children}
        {toasts.map((toast) => (
          <Primitive.Root
            key={toast.id}
            onOpenChange={(open) => (open ? undefined : dismiss(toast.id))}
            className="flex items-center gap-2 rounded-popover bg-text py-2 pr-2 pl-4 text-surface shadow-popover data-[state=closed]:animate-fade-out data-[state=open]:animate-slide-up"
          >
            <Primitive.Description className="flex-1 text-body">
              {toast.message}
            </Primitive.Description>
            {toast.action ? (
              <Primitive.Action
                altText={toast.action.label}
                onClick={toast.action.onPress}
                className="h-button-sm cursor-pointer rounded-control px-2.5 font-heavy text-small underline underline-offset-3 hover:bg-surface/10"
              >
                {toast.action.label}
              </Primitive.Action>
            ) : null}
            <Primitive.Close
              aria-label={t("actions.dismiss")}
              className="grid size-button-sm cursor-pointer place-items-center rounded-control hover:bg-surface/10"
            >
              <X aria-hidden="true" size={15} />
            </Primitive.Close>
          </Primitive.Root>
        ))}
        <Primitive.Viewport className="fixed right-4 bottom-4 z-50 flex w-96 max-w-[calc(100vw-2rem)] flex-col gap-2 outline-none" />
      </Primitive.Provider>
    </ToastContext.Provider>
  );
}
