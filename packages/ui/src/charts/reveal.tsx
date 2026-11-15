/**
 * A clip that uncovers a chart left to right once (D-0231). A new `run` plays it again, as when the real data replaces
 * a loading skeleton; reaching `bleed` past the box keeps strokes on the edge whole.
 */
export function RevealClip({
  id,
  width,
  height,
  run,
  bleed = 0,
}: {
  id: string;
  width: number;
  height: number;
  run: string;
  bleed?: number;
}) {
  return (
    <clipPath id={id}>
      <rect
        key={run}
        x={-bleed}
        y={-bleed}
        width={width + 2 * bleed}
        height={height + 2 * bleed}
        className="origin-left animate-draw-in [transform-box:fill-box]"
      />
    </clipPath>
  );
}
