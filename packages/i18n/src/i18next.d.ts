import type { defaultNamespace, resources } from "./resources.ts";

// Typed resources: a key missing from the English catalog is a compile error.
declare module "i18next" {
  interface CustomTypeOptions {
    defaultNS: typeof defaultNamespace;
    resources: (typeof resources)["en"];
  }
}
