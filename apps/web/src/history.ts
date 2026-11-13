import type { LoadPointView } from "@ogcloud/api-client";

const HOUR_MS = 3_600_000;
// The controller samples once a minute (D-0213); a sample this close to an hour ago stands for it.
const TOLERANCE_MS = 5 * 60_000;

/** The sample taken an hour before `now`, or none while the history is younger than that. */
export function sampleHourAgo(
  points: readonly LoadPointView[],
  now: number,
): LoadPointView | undefined {
  const target = now - HOUR_MS;
  let nearest: LoadPointView | undefined;
  let gap = Number.POSITIVE_INFINITY;
  for (const point of points) {
    const distance = Math.abs(new Date(point.at).getTime() - target);
    if (distance < gap) {
      nearest = point;
      gap = distance;
    }
  }
  return gap <= TOLERANCE_MS ? nearest : undefined;
}
