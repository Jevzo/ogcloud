const RUNNING_STATES = new Set(["RUNNING"]);
const STARTING_STATES = new Set(["STARTING", "PREPARING"]);
const STOPPING_STATES = new Set(["STOPPING", "DRAINING"]);
const FAILED_STATES = new Set(["FAILED", "CRASHED"]);

const DATE_TIME_FORMATTER = new Intl.DateTimeFormat(undefined, {
  dateStyle: "medium",
  timeStyle: "short",
});

export const getServerStateTone = (state: string) => {
  const normalizedState = state.toUpperCase();

  if (RUNNING_STATES.has(normalizedState)) {
    return "bg-emerald-500/10 text-emerald-400";
  }

  if (STARTING_STATES.has(normalizedState) || STOPPING_STATES.has(normalizedState)) {
    return "bg-amber-500/10 text-amber-300";
  }

  if (FAILED_STATES.has(normalizedState)) {
    return "bg-red-500/10 text-red-300";
  }

  return "bg-slate-800 text-slate-300";
};

export const formatTps = (tps: number) => {
  if (!Number.isFinite(tps) || tps <= 0) {
    return "--";
  }

  return tps.toFixed(1);
};

export const formatMemoryMb = (memoryUsedMb: number) => {
  if (!Number.isFinite(memoryUsedMb) || memoryUsedMb <= 0) {
    return "--";
  }

  if (memoryUsedMb < 1024) {
    return `${Math.round(memoryUsedMb)} MB`;
  }

  return `${(memoryUsedMb / 1024).toFixed(1)} GB`;
};

const parseDateTimeInput = (value: string) => {
  const numericValue = Number(value);
  if (!Number.isFinite(numericValue)) {
    return new Date(value);
  }

  const isEpochSeconds = value.length <= 10;
  return new Date(isEpochSeconds ? numericValue * 1_000 : numericValue);
};

export const formatDateTime = (value: string | null | undefined) => {
  if (!value) {
    return "--";
  }

  const normalizedValue = value.trim();
  const parsed = parseDateTimeInput(normalizedValue);

  if (Number.isNaN(parsed.getTime())) {
    return "--";
  }

  return DATE_TIME_FORMATTER.format(parsed);
};
