import type {
  GroupOverview,
  GroupSettings,
  GroupView,
} from "@ogcloud/api-client";
import {
  getGroupGetQueryKey,
  useGroupGet,
  useGroupUpdate,
  useOverviewGet,
  useOverviewHistory,
  useRuntimeList,
} from "@ogcloud/api-client/react";
import { GroupUpdateBody } from "@ogcloud/api-client/zod";
import {
  Button,
  Card,
  type KpiDelta,
  KpiTile,
  Loading,
  Note,
  PageHeader,
  Tabs,
  TimeSeriesChart,
  Value,
} from "@ogcloud/ui";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  Construction,
  Info,
  Layers,
  Play,
  Plus,
  RotateCw,
  Trash2,
} from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { DetailFallback } from "../../detail-fallback.tsx";
import { EditorForm, useDetailSave } from "../../editing.tsx";
import { loadFailure } from "../../errors.ts";
import {
  contractSchema,
  keepingMaintenance,
  useViewSettingsForm,
} from "../../form.tsx";
import { useFormat } from "../../format.ts";
import { LoadFailed } from "../../load-failed.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan } from "../../permissions.ts";
import {
  placeholderGroup,
  placeholderGroupOverview,
  placeholderHistory,
} from "../../placeholders.ts";
import { RequestServerDialog } from "../servers/request-server-dialog.tsx";
import { useGroupActions } from "./group-actions.tsx";
import { GroupSettingsFields } from "./group-settings-fields.tsx";
import { GroupStatusBadge } from "./group-status.tsx";
import { TemplatesPanel } from "./templates-panel.tsx";

const LIVE_REFRESH_MS = 10_000;
const HISTORY_REFRESH_MS = 60_000;
const CHART_HEIGHT = 140;
const HOUR_MS = 3_600_000;
// A group this full warns, like its capacity meters.
const FULL_AT = 0.9;
// The header owns maintenance: the form shows no field for it, and a save keeps the value the edits began from.
const SettingsForm = contractSchema<{ settings: GroupSettings }>(
  z.object({ settings: GroupUpdateBody.shape.settings }),
);

/** One group's page: its settings edited in place with a save bar, its live load beside them (D-0210). */
export function GroupPage() {
  const { t } = useTranslation("web");
  const { groupId } = useParams({ from: "/app/groups/$groupId" });
  const group = useGroupGet(groupId);

  if (group.data === undefined && group.error !== null) {
    return (
      <DetailFallback
        error={group.error}
        icon={Layers}
        goneTitle={t("groups.detail.gone")}
        goneDescription={t("groups.detail.goneDescription")}
        failedTitle={t("groups.detail.failed")}
        back={
          <Link to="/groups" className="font-label text-accent">
            {t("groups.detail.back")}
          </Link>
        }
      />
    );
  }
  const view = group.data?.data;
  // A new key when the data arrives, so the form starts from it rather than from the placeholder.
  return (
    <Loading when={view === undefined}>
      <GroupEditor
        key={view === undefined ? "loading" : `loaded:${view.id}`}
        view={view ?? placeholderGroup}
      />
    </Loading>
  );
}

function GroupEditor({ view }: { view: GroupView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const canWrite = useCan("groups:write");
  const canRequest = useCan("servers:command");
  const runtime = useRuntimeList();
  // Live load comes from the overview, which a role may lack beside groups:read.
  const canSeeLoad = useCan("overview:read");
  const overview = useOverviewGet({
    query: { refetchInterval: LIVE_REFRESH_MS, enabled: canSeeLoad },
  });
  const actions = useGroupActions({
    onDeleted: () => void navigate({ to: "/groups" }),
  });
  const [requesting, setRequesting] = useState(false);
  const [tab, setTab] = useState<"settings" | "templates">("settings");
  const update = useGroupUpdate();
  const live = overview.data?.data.groups.find((group) => group.id === view.id);
  const outcome = useDetailSave(getGroupGetQueryKey(view.id));

  const { form, changes, saving } = useViewSettingsForm({
    view,
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({
        id: base.id,
        data: keepingMaintenance(settings, base),
      }),
    ...outcome,
  });

  const menu = canWrite
    ? [
        [
          {
            label: t("groups.actions.delete"),
            icon: Trash2,
            danger: true,
            onSelect: () => actions.request("delete", view.id),
          },
        ],
      ]
    : [];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={<Value>{view.id}</Value>}
        badges={live === undefined ? null : <GroupStatusBadge group={live} />}
        actions={
          <>
            <MoreMenu groups={menu} />
            {canWrite && view.settings.maintenance ? (
              <Button
                icon={RotateCw}
                onClick={() => actions.request("restart", view.id)}
              >
                {t("groups.actions.restart")}
              </Button>
            ) : null}
            {canWrite && !view.settings.maintenance ? (
              <Button
                icon={Construction}
                data-skeleton=""
                onClick={() => actions.request("startMaintenance", view.id)}
              >
                {t("groups.actions.startMaintenance")}
              </Button>
            ) : null}
            {canRequest ? (
              <Button
                kind="primary"
                icon={Plus}
                onClick={() => setRequesting(true)}
              >
                {t("servers.request.submit")}
              </Button>
            ) : null}
          </>
        }
      />
      {view.settings.maintenance ? (
        // The header's badge names the state; the note says what it means and carries the way out.
        <Note
          tone="warning"
          icon={Construction}
          action={
            canWrite ? (
              <Button
                size="sm"
                icon={Play}
                onClick={() => actions.request("endMaintenance", view.id)}
              >
                {t("groups.actions.endMaintenance")}
              </Button>
            ) : undefined
          }
        >
          {t("groups.detail.maintenance")}
        </Note>
      ) : null}
      <Tabs
        label={t("groups.detail.sections")}
        value={tab}
        onValueChange={setTab}
        tabs={[
          {
            value: "settings",
            label: t("groups.detail.settings"),
            keepMounted: true,
            content: (
              <EditorForm
                form={form}
                canWrite={canWrite}
                changes={changes}
                saving={saving}
                aside={
                  canSeeLoad ? (
                    <LivePanel groupId={view.id} live={live} />
                  ) : (
                    <Note tone="info" icon={Info}>
                      {t("groups.detail.liveHidden")}
                    </Note>
                  )
                }
              >
                <GroupSettingsFields
                  form={form}
                  fields="settings"
                  type={view.type}
                  profiles={runtime.data?.data ?? []}
                />
              </EditorForm>
            ),
          },
          {
            value: "templates",
            label: t("groups.detail.templates"),
            content: (
              <TemplatesPanel
                groupId={view.id}
                type={view.type}
                // Without the live load the warning shows, since a running server cannot be ruled out.
                staticRunning={
                  !canSeeLoad || (live?.running ?? 0) + (live?.pending ?? 0) > 0
                }
              />
            ),
          },
        ]}
      />
      {actions.dialogs}
      <RequestServerDialog
        open={requesting}
        onOpenChange={setRequesting}
        groupIds={[view.id]}
      />
    </div>
  );
}

/** What the group runs right now against an hour ago, and its players over the last day (D-0231). */
function LivePanel({
  groupId,
  live,
}: {
  groupId: string;
  live: GroupOverview | undefined;
}) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const history = useOverviewHistory(
    { groupId },
    { query: { refetchInterval: HISTORY_REFRESH_MS } },
  );
  const now = Date.now();
  const samples = (history.data?.data ?? placeholderHistory(now)).points;
  const points = samples.map((point) => ({
    at: new Date(point.at),
    value: point.players,
  }));
  const hourAgo = samples.find(
    (point) => new Date(point.at).getTime() >= now - HOUR_MS,
  );
  const failure = loadFailure(history);
  const load = live ?? placeholderGroupOverview;
  const delta = (change: number | undefined): KpiDelta | undefined =>
    change === undefined
      ? undefined
      : {
          change: format.signed(change),
          basis: t("overview.kpi.basis"),
          trend: change > 0 ? "up" : change < 0 ? "down" : "flat",
        };

  return (
    <>
      <Loading when={live === undefined}>
        <div className="grid grid-cols-2 gap-6">
          <KpiTile
            label={t("groups.columns.servers")}
            value={load.running}
            format={format.number}
            caption={
              load.pending > 0
                ? t("groups.starting", { count: load.pending })
                : undefined
            }
            delta={delta(
              hourAgo === undefined
                ? undefined
                : load.running - hourAgo.running,
            )}
          />
          <KpiTile
            label={t("groups.columns.players")}
            value={load.players}
            format={format.number}
            caption={t("groups.limit", { max: format.number(load.capacity) })}
            delta={delta(
              hourAgo === undefined
                ? undefined
                : load.players - hourAgo.players,
            )}
            warn={load.capacity > 0 && load.players / load.capacity >= FULL_AT}
          />
        </div>
      </Loading>
      <Card title={t("groups.detail.chartTitle")}>
        {failure !== undefined && history.data === undefined ? (
          <LoadFailed failure={failure} />
        ) : points.length < 2 ? (
          <p className="text-small text-text-3">
            {t("overview.players.empty")}
          </p>
        ) : (
          <Loading when={history.data === undefined}>
            <TimeSeriesChart
              label={t("groups.detail.chart", { id: groupId })}
              series={[{ name: t("overview.players.series"), points }]}
              height={CHART_HEIGHT}
              formatValue={format.number}
              formatTime={format.clock}
            />
          </Loading>
        )}
      </Card>
    </>
  );
}
