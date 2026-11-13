import { motion } from "@ogcloud/tokens";
import type { Transition } from "motion/react";

const MS_PER_SECOND = 1000;

type Curve = readonly [number, number, number, number];
const bezier = ([x1, y1, x2, y2]: Curve): [number, number, number, number] => [
  x1,
  y1,
  x2,
  y2,
];

/** Motion's default transition: a tween on the motion tokens, since workflow UI never springs (D-0231). */
export const tween: Transition = {
  duration: motion.durationMs.base / MS_PER_SECOND,
  ease: bezier(motion.easing.standard),
};

/** Leaving runs at three quarters of the entrance, on the exit curve. */
export const exitTween: Transition = {
  duration: (motion.durationMs.base * motion.exitRatio) / MS_PER_SECOND,
  ease: bezier(motion.easing.exit),
};
