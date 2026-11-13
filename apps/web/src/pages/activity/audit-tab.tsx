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
  Drawer,
  EmptyState,
  FilterBar,
  Input,
  type ListFilterSpec,
} from "@ogcloud/ui";
import { ScrollText } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Ago } from "../../ago.tsx";
import { agoColumn, monoColumn } from "../../columns.tsx";
import { TYPING_PAUSE_MS, useDebounced } from "../../debounce.ts";
import { DetailRow, Details } from "../../details.tsx";
import { loadFailure } from "../../errors.ts";
import { useFormat } from "../../format.ts";
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
  const format = useFormat();
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
  const [opened, setOpened] = useState<AuditEntryView | null>(null);
  const textFilter = (key: keyof Filters): ListFilterSpec => ({
    id: key,
    label: t(`activity.audit.filter.${key}`),
    control: (
      <Input
        mono
        aria-label={t(`activity.audit.filter.${key}`)}
        placeholder={
          key === "targetId" ? undefined : t(`activity.audit.example.${key}`)
        }
        value={filters[key]}
        onChange={(event) => set(key)(event.target.value)}
      />
    ),
    shown: filters[key].trim() === "" ? null : filters[key].trim(),
    clear: () => set(key)(""),
  });
  const timeFilter = (
    key: "from" | "until",
    value: Date | null,
    change: (value: Date | null) => void,
  ): ListFilterSpec => ({
    id: key,
    label: t(`activity.audit.filter.${key}`),
    control: (
      <DateTimePicker
        aria-label={t(`activity.audit.filter.${key}`)}
        value={value}
        onChange={change}
        now={new Date()}
      />
    ),
    shown: value === null ? null : format.dateTime(value),
    clear: () => change(null),
  });

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
    <div className="flex flex-col gap-6">
      <FilterBar
        filters={[
          textFilter("actor"),
          textFilter("action"),
          textFilter("targetType"),
          textFilter("targetId"),
          timeFilter("from", from, setFrom),
          timeFilter("until", until, setUntil),
        ]}
      />
      <Card flush>
        <DataTable
          label={t("activity.tabs.audit")}
          data={audit.items}
          failure={loadFailure(audit)}
          columns={auditColumns}
          getRowId={(entry) => entry.id}
          placeholder={placeholderAudit}
          loading={audit.pending}
          onRowOpen={setOpened}
          maxHeight="calc(100vh - 18rem)"
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
      <AuditDrawer entry={opened} onClose={() => setOpened(null)} />
    </div>
  );
}

/** One change in full, read-only: who made it, what it did to what, and what it carried (D-0231). */
function AuditDrawer({
  entry,
  onClose,
}: {
  entry: AuditEntryView | null;
  onClose: () => void;
}) {
  const { t } = useTranslation("web");
  if (entry === null) return null;
  const metadata = Object.entries(entry.metadata);
  return (
    <Drawer
      open
      onOpenChange={(open) => (open ? undefined : onClose())}
      title={<span className="font-mono">{entry.action}</span>}
    >
      {entry.summary === "" ? null : <p>{entry.summary}</p>}
      <Details>
        <DetailRow label={t("activity.audit.when")}>
          <Ago at={entry.occurredAt} className="" />
        </DetailRow>
        <DetailRow label={t("activity.audit.actor")}>
          <span className="font-mono text-mono break-all">{entry.actor}</span>
        </DetailRow>
        <DetailRow label={t("activity.audit.target")}>
          <span className="font-mono text-mono break-all">
            {`${entry.targetType} ${entry.targetId}`.trim()}
          </span>
        </DetailRow>
      </Details>
      {metadata.length === 0 ? null : (
        <section className="flex flex-col gap-2">
          <h3 className="text-heading">{t("activity.audit.details")}</h3>
          <Details>
            {metadata.map(([key, value]) => (
              <DetailRow key={key} label={key}>
                <span className="font-mono text-mono break-all">{value}</span>
              </DetailRow>
            ))}
          </Details>
        </section>
      )}
    </Drawer>
  );
}
