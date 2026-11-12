import type { AuditEntryView, LogAuditParams } from "@ogcloud/api-client";
import {
  getLogAuditQueryKey,
  useLogAuditHook,
} from "@ogcloud/api-client/react";
import {
  Card,
  columnsFor,
  DataTable,
  DateTimePicker,
  EmptyState,
  Field,
  Input,
} from "@ogcloud/ui";
import { ScrollText } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { agoColumn, monoColumn } from "../../columns.tsx";
import { TYPING_PAUSE_MS, useDebounced } from "../../debounce.ts";
import { loadFailure } from "../../errors.ts";
import { LoadMore } from "../../load-more.tsx";
import { PAGE_SIZE, usePaged } from "../../paged.ts";
import { placeholderAudit } from "../../placeholders.ts";

const columns = columnsFor<AuditEntryView>();

interface Filters {
  actor: string;
  action: string;
  targetType: string;
  targetId: string;
}

/** Only the filters that are set, as the API takes them (D-0213). */
function paramsOf(
  filters: Filters,
  from: Date | null,
  until: Date | null,
): LogAuditParams {
  const text = Object.fromEntries(
    Object.entries(filters)
      .map(([key, value]) => [key, value.trim()] as const)
      .filter(([, value]) => value !== ""),
  );
  return {
    ...text,
    ...(from === null ? {} : { from: from.toISOString() }),
    ...(until === null ? {} : { until: until.toISOString() }),
  };
}

/** Every change made through the API or in game, newest first, narrowed by who, what, which and when. */
export function AuditTab() {
  const { t } = useTranslation("web");
  const fetchAudit = useLogAuditHook();
  const [filters, setFilters] = useState<Filters>({
    actor: "",
    action: "",
    targetType: "",
    targetId: "",
  });
  const [from, setFrom] = useState<Date | null>(null);
  const [until, setUntil] = useState<Date | null>(null);
  const settled = useDebounced(filters, TYPING_PAUSE_MS);
  const params = paramsOf(settled, from, until);
  const audit = usePaged(
    getLogAuditQueryKey(params),
    async (cursor) =>
      (
        await fetchAudit({
          ...params,
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
  );
  const set = (key: keyof Filters) => (value: string) =>
    setFilters((current) => ({ ...current, [key]: value }));

  const auditColumns = useMemo(
    () =>
      columns.columns([
        agoColumn(
          columns,
          "occurredAt",
          t("activity.audit.when"),
          (entry) => entry.occurredAt,
        ),
        monoColumn(
          columns,
          "actor",
          t("activity.audit.actor"),
          (entry) => entry.actor,
          true,
        ),
        monoColumn(
          columns,
          "action",
          t("activity.audit.action"),
          (entry) => entry.action,
        ),
        monoColumn(
          columns,
          "target",
          t("activity.audit.target"),
          (entry) => `${entry.targetType} ${entry.targetId}`.trim(),
          true,
        ),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-4">
      <Card title={t("activity.audit.filters")}>
        <div className="grid grid-cols-3 gap-x-4 gap-y-3">
          {(["actor", "action", "targetType"] as const).map((key) => (
            <Field key={key} label={t(`activity.audit.filter.${key}`)}>
              {(control) => (
                <Input
                  {...control}
                  mono
                  placeholder={t(`activity.audit.example.${key}`)}
                  value={filters[key]}
                  onChange={(event) => set(key)(event.target.value)}
                />
              )}
            </Field>
          ))}
          <Field label={t("activity.audit.filter.targetId")}>
            {(control) => (
              <Input
                {...control}
                mono
                value={filters.targetId}
                onChange={(event) => set("targetId")(event.target.value)}
              />
            )}
          </Field>
          <Field label={t("activity.audit.filter.from")}>
            {(control) => (
              <DateTimePicker
                {...control}
                value={from}
                onChange={setFrom}
                now={new Date()}
              />
            )}
          </Field>
          <Field label={t("activity.audit.filter.until")}>
            {(control) => (
              <DateTimePicker
                {...control}
                value={until}
                onChange={setUntil}
                now={new Date()}
              />
            )}
          </Field>
        </div>
      </Card>
      <Card flush>
        <DataTable
          label={t("activity.tabs.audit")}
          data={audit.items}
          failure={loadFailure(audit)}
          columns={auditColumns}
          getRowId={(entry) => entry.id}
          placeholder={placeholderAudit}
          loading={audit.pending}
          maxHeight="calc(100vh - 24rem)"
          empty={
            <EmptyState
              icon={ScrollText}
              title={t("activity.audit.empty")}
              description={t("activity.audit.emptyDescription")}
            />
          }
        />
        <LoadMore paged={audit} />
      </Card>
    </div>
  );
}
