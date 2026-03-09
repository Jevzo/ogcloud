import type { ComponentType } from "react";
import { AnimatePresence, motion } from "motion/react";
import { FiAlertCircle, FiCheckCircle, FiInfo, FiX } from "react-icons/fi";

type ToastTone = "error" | "success" | "info";

export interface AppToastItem {
    id: string;
    message: string;
    tone: ToastTone;
    onDismiss?: () => void;
}

interface AppToastsProps {
    items: AppToastItem[];
}

const TONE_STYLES: Record<
    ToastTone,
    { container: string; text: string; icon: ComponentType<{ className?: string }> }
> = {
    error: {
        container: "border-red-500/20 bg-red-500/10",
        text: "text-red-200",
        icon: FiAlertCircle,
    },
    success: {
        container: "border-emerald-500/20 bg-emerald-500/10",
        text: "text-emerald-200",
        icon: FiCheckCircle,
    },
    info: {
        container: "border-primary/20 bg-primary/10",
        text: "text-slate-100",
        icon: FiInfo,
    },
};

const AppToasts = ({ items }: AppToastsProps) => (
    <div
        className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-3"
        aria-live="polite"
        aria-atomic="false"
    >
        <AnimatePresence initial={false}>
            {items.map((item) => {
                const toneStyle = TONE_STYLES[item.tone];
                const Icon = toneStyle.icon;
                const liveRole = item.tone === "error" ? "alert" : "status";

                return (
                    <motion.div
                        key={item.id}
                        initial={{ y: 18, opacity: 0, scale: 0.97 }}
                        animate={{ y: 0, opacity: 1, scale: 1 }}
                        exit={{ y: 18, opacity: 0, scale: 0.97 }}
                        transition={{ duration: 0.22, ease: "easeOut" }}
                        role={liveRole}
                        className={`pointer-events-auto rounded-xl border px-4 py-3 shadow-2xl backdrop-blur-sm ${toneStyle.container}`}
                    >
                        <div className="flex items-start gap-3">
                            <Icon className={`mt-0.5 h-4 w-4 shrink-0 ${toneStyle.text}`} />
                            <p className={`min-w-0 flex-1 text-sm leading-6 ${toneStyle.text}`}>
                                {item.message}
                            </p>
                            {item.onDismiss && (
                                <button
                                    type="button"
                                    onClick={item.onDismiss}
                                    className={`rounded-lg p-1 transition-colors hover:bg-slate-950/20 ${toneStyle.text}`}
                                    aria-label="Dismiss"
                                >
                                    <FiX className="h-4 w-4" />
                                </button>
                            )}
                        </div>
                    </motion.div>
                );
            })}
        </AnimatePresence>
    </div>
);

export default AppToasts;
