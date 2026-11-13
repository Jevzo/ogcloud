import { Meter, Value } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";
import { useFormat } from "./format.ts";

/** How full something is: a meter, the count, and its limit after it in muted meta text (D-0231). */
export function Load({
  value,
  max,
  kind = "capacity",
}: {
  value: number;
  max: number;
  kind?: "capacity" | "progress";
}) {
  const { t } = useTranslation("web");
  const format = useFormat();
  return (
    <span className="inline-flex items-center gap-2.5 tabular-nums">
      <Meter value={value} max={max} kind={kind} />
      <Value>
        {format.number(value)}{" "}
        <span className="text-meta text-text-3">
          {t("groups.limit", { max: format.number(max) })}
        </span>
      </Value>
    </span>
  );
}

/** Running servers, with those still starting after them, muted. */
export function ServerCount({
  running,
  pending,
}: {
  running: number;
  pending: number;
}) {
  const { t } = useTranslation("web");
  const format = useFormat();
  return (
    <Value className="tabular-nums">
      {format.number(running)}
      {pending > 0 ? (
        <span className="text-meta text-text-3">
          {" "}
          {t("groups.starting", { count: pending })}
        </span>
      ) : null}
    </Value>
  );
}
