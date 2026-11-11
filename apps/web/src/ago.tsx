import { useFormat } from "./format.ts";

/** How long ago a moment was, muted, in the interface's language; a dash when there is none. */
export function Ago({ at }: { at: string | null | undefined }) {
  const format = useFormat();
  return (
    <span className="text-text-2">
      {at == null ? "—" : format.ago(new Date(at), Date.now())}
    </span>
  );
}
