import { fontWeights, radius, themes, typeScale } from "./tokens.ts";

/** surface2 -> surface-2, accentSoft -> accent-soft: the names CSS variables and Tailwind utilities use. */
export const kebab = (name: string) =>
  name.replace(/([a-z])([A-Z0-9])/g, "$1-$2").toLowerCase();

/** The utility names each Tailwind theme namespace holds, e.g. `bg-surface-2`, `text-display`, `rounded-card`. */
export const utilityNames = {
  colour: Object.keys(themes.light.colours).map(kebab),
  shadow: Object.keys(themes.light.shadows),
  radius: Object.keys(radius),
  text: [...Object.keys(typeScale).map(kebab), "table"],
  fontWeight: Object.keys(fontWeights),
} as const;
