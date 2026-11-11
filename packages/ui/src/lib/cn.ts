import { utilityNames } from "@ogcloud/tokens";
import { type ClassValue, clsx } from "clsx";
import { extendTailwindMerge } from "tailwind-merge";

// tailwind-merge must know OgCloud's token names, or it reads `text-display` as a colour and drops one of
// `text-display text-text-2`.
const merge = extendTailwindMerge({
  override: {
    theme: {
      color: [...utilityNames.colour],
      text: [...utilityNames.text],
      radius: [...utilityNames.radius],
      shadow: [...utilityNames.shadow],
      "font-weight": [...utilityNames.fontWeight],
      font: ["sans", "mono", "game"],
      ease: ["standard"],
      container: ["form"],
    },
  },
  extend: {
    theme: {
      spacing: [
        "row",
        "control",
        "nav-item",
        "button-sm",
        "button-lg",
        "sidebar",
        "header",
      ],
    },
  },
});

/** Joins class names, letting later utilities win over earlier ones of the same kind. */
export function cn(...classes: ClassValue[]): string {
  return merge(clsx(classes));
}
