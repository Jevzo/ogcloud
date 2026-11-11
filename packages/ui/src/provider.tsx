import { motion } from "@ogcloud/tokens";
import { MotionConfig } from "motion/react";
import { Tooltip } from "radix-ui";
import type { ReactNode } from "react";
import { ToastProvider } from "./components/toast.tsx";

const TOOLTIP_DELAY_MS = 400;

/** Wraps an app once: Motion's spring and reduced-motion handling, tooltips and toasts. */
export function UiProvider({ children }: { children: ReactNode }) {
  return (
    <MotionConfig
      reducedMotion="user"
      transition={{ type: "spring", ...motion.spring }}
    >
      <Tooltip.Provider delayDuration={TOOLTIP_DELAY_MS}>
        <ToastProvider>{children}</ToastProvider>
      </Tooltip.Provider>
    </MotionConfig>
  );
}
