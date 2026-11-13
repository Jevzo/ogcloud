import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from "lucide-react";
import { Toast as Primitive } from "radix-ui";
import {
  createContext,
  type FocusEvent,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { useTranslation } from "react-i18next";
import { cn } from "../lib/cn.ts";

/** What a toast reports: something done, news, something to look at, or something that failed. */
export type ToastTone = "success" | "info" | "warning" | "danger";

export interface ToastOptions {
  message: string;
  /** Success when left out. */
  tone?: ToastTone;
  /** One action, typically Undo; its label names it. */
  action?: { label: string; onPress: () => void };
}

interface QueuedToast extends ToastOptions {
  id: number;
}

// Anything but a confirmation stays longer, since the reader did not just cause it; a failure stays until
// dismissed, since the reader must act on it (D-0231).
const LIFETIME_MS: Record<ToastTone, number | null> = {
  success: 5000,
  info: 8000,
  warning: 8000,
  danger: null,
};

/** More than this and the oldest leaves first, unless it is a failure, which waits for the reader. */
const MAX_TOASTS = 3;

function withinCap(toasts: QueuedToast[]): QueuedToast[] {
  let excess = toasts.length - MAX_TOASTS;
  return toasts.filter((toast) => {
    if (excess <= 0 || toast.tone === "danger") return true;
    excess -= 1;
    return false;
  });
}

const ICONS = {
  success: CircleCheck,
  info: Info,
  warning: TriangleAlert,
  danger: CircleAlert,
} as const;

const ICON_COLOURS: Record<ToastTone, string> = {
  success: "text-success",
  info: "text-info",
  warning: "text-warning",
  danger: "text-danger",
};

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
  const [toasts, setToasts] = useState<QueuedToast[]>([]);
  const nextId = useRef(0);

  const show = useCallback((toast: ToastOptions) => {
    const id = nextId.current++;
    setToasts((current) => withinCap([...current, { ...toast, id }]));
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
          <ToastItem key={toast.id} toast={toast} dismiss={dismiss} />
        ))}
        <Primitive.Viewport className="fixed right-4 bottom-4 z-50 flex max-w-toast flex-col items-end gap-2 outline-none" />
      </Primitive.Provider>
    </ToastContext.Provider>
  );
}

function ToastItem({
  toast,
  dismiss,
}: {
  toast: QueuedToast;
  dismiss: (id: number) => void;
}) {
  const { t } = useTranslation("ui");
  const tone = toast.tone ?? "success";
  const Icon = ICONS[tone];
  const close = useCallback(() => dismiss(toast.id), [dismiss, toast.id]);
  const hold = useCloseTimer(LIFETIME_MS[tone], close);
  return (
    <Primitive.Root
      // Radix's own timer also stops while the window is blurred, as with DevTools focused; ours does not.
      duration={Number.POSITIVE_INFINITY}
      type={tone === "danger" ? "foreground" : "background"}
      onOpenChange={(open) => (open ? undefined : close())}
      {...hold}
      className="flex w-max max-w-full items-center gap-2.5 glass rounded-card border border-border py-2 pr-2 pl-3.5 text-text shadow-popover data-[state=closed]:animate-fade-out data-[state=open]:animate-slide-up"
    >
      <Icon
        aria-hidden="true"
        size={16}
        className={cn("shrink-0", ICON_COLOURS[tone])}
      />
      <Primitive.Description className="min-w-0 flex-1 text-body">
        {toast.message}
      </Primitive.Description>
      {toast.action ? (
        <Primitive.Action
          altText={toast.action.label}
          onClick={toast.action.onPress}
          className="h-button-sm shrink-0 cursor-pointer rounded-control px-2.5 font-bold text-accent text-small hover:bg-accent-soft"
        >
          {toast.action.label}
        </Primitive.Action>
      ) : null}
      <Primitive.Close
        aria-label={t("actions.dismiss")}
        className="grid size-button-sm shrink-0 cursor-pointer place-items-center rounded-control text-text-3 hover:bg-text/7 hover:text-text"
      >
        <X aria-hidden="true" size={15} />
      </Primitive.Close>
    </Primitive.Root>
  );
}

/**
 * Calls [close] after [lifetime] ms, counting only while neither the pointer nor the keyboard focus is on the toast,
 * so a reader can always finish it; without a lifetime, only the reader closes it.
 */
function useCloseTimer(lifetime: number | null, close: () => void) {
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const remaining = useRef(lifetime ?? 0);
  const held = hovered || focused;
  useEffect(() => {
    if (held || lifetime === null) return;
    const started = performance.now();
    const timer = window.setTimeout(close, remaining.current);
    return () => {
      window.clearTimeout(timer);
      remaining.current -= performance.now() - started;
    };
  }, [held, close, lifetime]);
  return {
    onPointerEnter: () => setHovered(true),
    onPointerLeave: () => setHovered(false),
    onFocus: () => setFocused(true),
    onBlur: (event: FocusEvent<HTMLLIElement>) => {
      if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
    },
  };
}
