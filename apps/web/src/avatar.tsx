import { cn } from "@ogcloud/ui";
import { UserRound } from "lucide-react";
import { useState } from "react";

const INITIALS = 2;
// Heads come from mc-heads.net, which the dashboard's CSP admits for images (D-0235); twice the size for sharp
// pixels on dense screens.
const HEAD_SERVICE = "https://mc-heads.net/avatar";
const HEAD_SCALE = 2;

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
      src={`${HEAD_SERVICE}/${uuid.replaceAll("-", "")}/${size * HEAD_SCALE}`}
      alt=""
      width={size}
      height={size}
      onError={() => setFailed(true)}
      className={cn(look, "[image-rendering:pixelated]")}
    />
  );
}
