import { MotionConfig } from "motion/react";
import { Tooltip } from "radix-ui";
import { type ReactNode, useState } from "react";
import { ToastProvider } from "./components/toast.tsx";
import { tween } from "./lib/motion.ts";
import { NonceContext, readCspNonce } from "./nonce.ts";

const TOOLTIP_DELAY_MS = 400;

/** Wraps an app once: the CSP nonce, Motion's default tween and reduced-motion handling, tooltips and toasts. */
export function UiProvider({ children }: { children: ReactNode }) {
  // Read once, before any child inserts a style; libraries that take no prop read webpack's global.
  const [nonce] = useState(() => {
    const read = readCspNonce();
    globalThis.__webpack_nonce__ = read;
    return read;
  });
  return (
    <NonceContext.Provider value={nonce}>
      <MotionConfig
        {...(nonce === undefined ? {} : { nonce })}
        reducedMotion="user"
        transition={tween}
      >
        <Tooltip.Provider delayDuration={TOOLTIP_DELAY_MS}>
          <ToastProvider>{children}</ToastProvider>
        </Tooltip.Provider>
      </MotionConfig>
    </NonceContext.Provider>
  );
}
