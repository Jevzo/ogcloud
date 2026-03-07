const LATENCY_WINDOW_MS = 10 * 60 * 1000;

interface LatencySample {
  timestamp: number;
  latencyMs: number;
}

let sessionActive = false;
let samples: LatencySample[] = [];

const pruneSamples = (now = Date.now(), windowMs = LATENCY_WINDOW_MS) => {
  const minTimestamp = now - windowMs;
  samples = samples.filter((sample) => sample.timestamp >= minTimestamp);
};

export const startApiLatencySession = () => {
  sessionActive = true;
  samples = [];
};

export const endApiLatencySession = () => {
  sessionActive = false;
  samples = [];
};

export const recordApiLatencySample = (latencyMs: number, timestamp = Date.now()) => {
  if (!sessionActive || !Number.isFinite(latencyMs) || latencyMs <= 0) {
    return;
  }

  samples.push({
    timestamp,
    latencyMs: Math.round(latencyMs),
  });
  pruneSamples(timestamp);
};

export const getAverageApiLatency = (windowMs = LATENCY_WINDOW_MS) => {
  if (!sessionActive) {
    return null;
  }

  pruneSamples(Date.now(), windowMs);

  if (samples.length === 0) {
    return null;
  }

  const total = samples.reduce((sum, sample) => sum + sample.latencyMs, 0);
  return Math.round(total / samples.length);
};

export const API_LATENCY_WINDOW_MS = LATENCY_WINDOW_MS;
