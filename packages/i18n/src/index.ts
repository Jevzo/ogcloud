import i18next, { type i18n } from "i18next";
import { defaultLanguage, defaultNamespace, resources } from "./resources.ts";

export { defaultLanguage, defaultNamespace, resources } from "./resources.ts";

/** Creates an initialised i18next instance shared by the web, desktop and mobile apps. */
export function createI18n(language: string = defaultLanguage): i18n {
  const instance = i18next.createInstance();
  instance.init({
    lng: language,
    fallbackLng: defaultLanguage,
    defaultNS: defaultNamespace,
    resources,
    // Catalogs are bundled, so initialisation completes synchronously.
    initAsync: false,
    // React escapes rendered values itself.
    interpolation: { escapeValue: false },
  });
  return instance;
}
