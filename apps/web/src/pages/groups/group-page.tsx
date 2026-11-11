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
  Meter,
  PageHeader,
  Skeleton,
  Tabs,
  TimeSeriesChart,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import {
  Construction,
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
import { EditorForm, useSaved, useSaveFailure } from "../../editing.tsx";
import { contractSchema, useSettingsForm } from "../../form.tsx";
import { useFormat } from "../../format.ts";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan } from "../../permissions.ts";
import { RequestServerDialog } from "../servers/request-server-dialog.tsx";
import { useGroupActions } from "./group-actions.tsx";
import { GroupSettingsFields } from "./group-settings-fields.tsx";
import { GroupStatusBadge } from "./group-status.tsx";
import { TemplatesPanel } from "./templates-panel.tsx";

const LIVE_REFRESH_MS = 10_000;
const HISTORY_REFRESH_MS = 60_000;
const CHART_HEIGHT = 120;
// Maintenance has its own action in the header, so the form leaves it out.
const SettingsForm = contractSchema<{ settings: GroupSettings }>(
  z.object({ settings: GroupUpdateBody.shape.settings }),
);

/** One group's page: its settings edited in place with a save bar, its live load beside them (D-0210). */
export function GroupPage() {
  const { t } = useTranslation("web");
  const { groupId } = useParams({ from: "/app/groups/$groupId" });
  const group = useGroupGet(groupId);

  if (group.data === undefined) {
    return (
      <DetailFallback
        error={group.error}
        icon={Layers}
        goneTitle={t("groups.detail.gone")}
        goneDescription={t("groups.detail.goneDescription")}
        failedTitle={t("groups.detail.failed")}
        back={
          <Link to="/groups" className="font-label text-accent-text">
            {t("groups.detail.back")}
          </Link>
        }
      />
    );
  }
  // A new version remounts the form, so a save or a reload starts it from what the API holds.
  return <GroupEditor key={group.data.data.version} view={group.data.data} />;
}

function GroupEditor({ view }: { view: GroupView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const client = useQueryClient();
  const canWrite = useCan("groups:write");
  const canRequest = useCan("servers:command");
  const runtime = useRuntimeList();
  const overview = useOverviewGet({
    query: { refetchInterval: LIVE_REFRESH_MS },
  });
  const actions = useGroupActions({
    onDeleted: () => void navigate({ to: "/groups" }),
  });
  const [requesting, setRequesting] = useState(false);
  const [tab, setTab] = useState<"settings" | "templates">("settings");
  const update = useGroupUpdate();
  const live = overview.data?.data.groups.find((group) => group.id === view.id);
  const saved = useSaved(getGroupGetQueryKey(view.id));
  const saveFailed = useSaveFailure(() =>
    client.invalidateQueries({ queryKey: getGroupGetQueryKey(view.id) }),
  );

  const { form, changes, saving } = useSettingsForm({
    settings: view.settings,
    schema: SettingsForm,
    save: (settings) =>
      update
        .mutateAsync({
          id: view.id,
          data: {
            settings: { ...settings, maintenance: view.settings.maintenance },
            version: view.version,
          },
        })
        .then(saved, saveFailed),
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
    <div className="flex flex-col gap-4">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {view.id}
            {live === undefined ? null : <GroupStatusBadge group={live} />}
          </span>
        }
        description={t("groups.detail.description", {
          type: t(`groups.type.${view.type}`),
          profile: view.settings.profileId,
        })}
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
            {canWrite ? (
              view.settings.maintenance ? (
                <Button
                  icon={Play}
                  onClick={() => actions.request("endMaintenance", view.id)}
                >
                  {t("groups.actions.endMaintenance")}
                </Button>
              ) : (
                <Button
                  icon={Construction}
                  onClick={() => actions.request("startMaintenance", view.id)}
                >
                  {t("groups.actions.startMaintenance")}
                </Button>
              )
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
      <Tabs
        label={t("groups.detail.sections")}
        value={tab}
        onValueChange={setTab}
        tabs={[
          {
            value: "settings",
            label: t("groups.detail.settings"),
            content: (
              <EditorForm
                form={form}
                canWrite={canWrite}
                changes={changes}
                saving={saving}
                aside={<LivePanel groupId={view.id} live={live} />}
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
                staticRunning={(live?.running ?? 0) + (live?.pending ?? 0) > 0}
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

/** What the group runs right now and its players over the last day, beside the settings on wide screens. */
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
  const points = (history.data?.data.points ?? []).map((point) => ({
    at: new Date(point.at),
    value: point.players,
  }));

  return (
    <Card title={t("groups.detail.live")}>
      {live === undefined ? (
        <Skeleton className="h-24" />
      ) : (
        <div className="flex flex-col gap-3">
          <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-6 gap-y-2">
            <dt className="text-text-3">{t("groups.columns.servers")}</dt>
            <dd className="m-0">
              {format.number(live.running)}
              {live.pending > 0 ? (
                <span className="text-text-3">
                  {" "}
                  {t("groups.starting", { count: live.pending })}
                </span>
              ) : null}
            </dd>
            <dt className="text-text-3">{t("groups.columns.players")}</dt>
            <dd className="m-0 inline-flex items-center gap-2.5">
              <Meter value={live.players} max={live.capacity} />
              {t("groups.load", {
                players: format.number(live.players),
                capacity: format.number(live.capacity),
              })}
            </dd>
          </dl>
          {points.length < 2 ? null : (
            <TimeSeriesChart
              label={t("groups.detail.chart", { id: groupId })}
              series={[{ name: t("overview.players.series"), points }]}
              height={CHART_HEIGHT}
              formatValue={format.number}
              formatTime={format.clock}
            />
          )}
        </div>
      )}
    </Card>
  );
}
