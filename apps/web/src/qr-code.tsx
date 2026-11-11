import { generate } from "lean-qr";
import { toSvgPath } from "lean-qr/extras/svg";

// Four modules of quiet zone, as scanners expect around a QR code.
const QUIET_ZONE = 4;

/** A QR code drawn as one SVG path, so the page needs no images or data URLs. */
export function QrCode({ value, label }: { value: string; label: string }) {
  const code = generate(value);
  const size = code.size + 2 * QUIET_ZONE;
  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`${-QUIET_ZONE} ${-QUIET_ZONE} ${size} ${size}`}
      className="size-44 rounded-control bg-qr-ground"
      shapeRendering="crispEdges"
    >
      <path d={toSvgPath(code)} className="fill-qr-ink" />
    </svg>
  );
}
