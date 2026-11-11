import common from "./locales/en/common.json" with { type: "json" };
import ui from "./locales/en/ui.json" with { type: "json" };

export const defaultLanguage = "en";
export const defaultNamespace = "common";

/** English is the source catalog; other languages are added as translations of it (D-0050). */
export const resources = {
  en: { common, ui },
} as const;
