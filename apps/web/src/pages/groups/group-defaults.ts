import type {
  GroupSettings,
  GroupViewType,
  RuntimeProfileView,
} from "@ogcloud/api-client";

interface TypeDefaults {
  scaling: GroupSettings["scaling"];
  resources: GroupSettings["resources"];
  platform: RuntimeProfileView["platform"];
}

const SCALE_UP = 0.8;
const SCALE_DOWN = 0.3;
const COOLDOWN_SECONDS = 60;
const DRAIN_SECONDS = 60;

// Starting points only: Paper needs 1.5 GiB at least (D-0177), and the API checks every value like any edit.
const BY_TYPE: Record<GroupViewType, TypeDefaults> = {
  DYNAMIC: {
    scaling: {
      minOnline: 1,
      maxInstances: 10,
      playersPerServer: 50,
      scaleUpThreshold: SCALE_UP,
      scaleDownThreshold: SCALE_DOWN,
      cooldownSeconds: COOLDOWN_SECONDS,
    },
    resources: { memoryRequest: "2Gi", memoryLimit: "2Gi", cpuRequest: "1" },
    platform: "PAPER",
  },
  STATIC: {
    scaling: {
      minOnline: 1,
      maxInstances: 1,
      playersPerServer: 100,
      scaleUpThreshold: SCALE_UP,
      scaleDownThreshold: SCALE_DOWN,
      cooldownSeconds: COOLDOWN_SECONDS,
    },
    resources: { memoryRequest: "3Gi", memoryLimit: "3Gi", cpuRequest: "2" },
    platform: "PAPER",
  },
  PROXY: {
    scaling: {
      minOnline: 1,
      maxInstances: 3,
      playersPerServer: 500,
      scaleUpThreshold: SCALE_UP,
      scaleDownThreshold: SCALE_DOWN,
      cooldownSeconds: COOLDOWN_SECONDS,
    },
    resources: { memoryRequest: "1Gi", memoryLimit: "1Gi", cpuRequest: "500m" },
    platform: "VELOCITY",
  },
};

/** The settings a new group of this type starts with, on the first matching runtime profile. */
export function newGroupSettings(
  type: GroupViewType,
  profiles: readonly RuntimeProfileView[],
): GroupSettings {
  const defaults = BY_TYPE[type];
  const profile = profiles.find(
    (candidate) => candidate.platform === defaults.platform,
  );
  return {
    profileId: profile?.id ?? "",
    scaling: defaults.scaling,
    drainTimeoutSeconds: DRAIN_SECONDS,
    maintenance: false,
    resources: { ...defaults.resources, cpuLimit: null },
    jvmFlags: "",
    image: null,
    storageSize: null,
  };
}
