export const BACKEND_RUNTIME_PROFILE_VALUES = ["LEGACY_1_8_8", "MODERN_1_21_11"] as const;

export type BackendRuntimeProfile = (typeof BACKEND_RUNTIME_PROFILE_VALUES)[number];

export const RUNTIME_BUNDLE_SCOPE_VALUES = ["VELOCITY", "PAPER_1_21_11", "PAPER_1_8_8"] as const;

export type RuntimeBundleScope = (typeof RUNTIME_BUNDLE_SCOPE_VALUES)[number];
