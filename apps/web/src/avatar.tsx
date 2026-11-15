import { cn } from "@ogcloud/ui";
import { UserRound } from "lucide-react";
import { useState } from "react";

const INITIALS = 2;
// Heads and bodies come from mc-heads.net, which the dashboard's CSP admits for images (D-0235, D-0238); twice the
// size for sharp pixels on dense screens.
const SKIN_SERVICE = "https://mc-heads.net";
const SKIN_SCALE = 2;
// The isometric body render is about 2.4 times as tall as it is wide.
const BODY_ASPECT = 2.4;

const skin = (kind: "avatar" | "body", uuid: string, width: number) =>
  `${SKIN_SERVICE}/${kind}/${uuid.replaceAll("-", "")}/${width * SKIN_SCALE}`;

/** A user as the first letters of their name on the accent tint. */
export function Initials({
  name,
  className,
}: {
  name: string;
  className?: string;
}) {
  return (
    <span
      aria-hidden="true"
      data-skeleton=""
      className={cn(
        "grid shrink-0 place-items-center rounded-full bg-accent-soft font-bold text-accent",
        className,
      )}
    >
      {name.slice(0, INITIALS).toUpperCase()}
    </span>
  );
}

/** A player's head as the game draws it, or a neutral stand-in when the head service does not answer. */
export function PlayerHead({ uuid, size }: { uuid: string; size: number }) {
  const [failed, setFailed] = useState(false);
  const look = "shrink-0 rounded-game";
  return failed ? (
    <span
      aria-hidden="true"
      className={cn(look, "grid place-items-center bg-surface-3 text-text-3")}
      style={{ width: size, height: size }}
    >
      <UserRound size={size / 2} strokeWidth={1.5} />
    </span>
  ) : (
    <img
      src={skin("avatar", uuid, size)}
      alt=""
      width={size}
      height={size}
      onError={() => setFailed(true)}
      className={cn(look, "[image-rendering:pixelated]")}
    />
  );
}

/** A player's whole skin as an isometric figure, or a neutral stand-in when the service does not answer (D-0238). */
export function PlayerBody({ uuid, width }: { uuid: string; width: number }) {
  const [failed, setFailed] = useState(false);
  const height = Math.round(width * BODY_ASPECT);
  return failed ? (
    <span
      aria-hidden="true"
      className="grid place-items-center text-text-3"
      style={{ width, height }}
    >
      <UserRound size={width / 2} strokeWidth={1} />
    </span>
  ) : (
    <img
      src={skin("body", uuid, width)}
      alt=""
      width={width}
      height={height}
      onError={() => setFailed(true)}
      className="object-contain [image-rendering:pixelated]"
    />
  );
}
