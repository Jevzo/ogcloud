import type { NpcSettings, NpcView } from "@ogcloud/api-client";
import {
  getNpcGetQueryKey,
  getNpcListQueryKey,
  useNpcGet,
  useNpcUpdate,
} from "@ogcloud/api-client/react";
import { NpcUpdateBody } from "@ogcloud/api-client/zod";
import { Card, CopyField, Loading, PageHeader, Value } from "@ogcloud/ui";
import { useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { Bot, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { DetailFallback } from "../../detail-fallback.tsx";
import { EditorForm, useDetailSave } from "../../editing.tsx";
import { contractSchema, useViewSettingsForm } from "../../form.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan } from "../../permissions.ts";
import { placeholderNpc } from "../../placeholders.ts";
import { invalidatePaths } from "../../queries.ts";
import { useBackendGroupIds } from "../groups/group-ids.ts";
import { useDeleteNpc } from "./npc-delete.tsx";
import { NpcFields } from "./npc-fields.tsx";
import { NpcLook } from "./npc-look.tsx";

const SettingsForm = contractSchema<{ settings: NpcSettings }>(
  z.object({ settings: NpcUpdateBody }),
);

/** One NPC's page: its look, place and clicks edited in place, its nametag previewed beside them. */
export function NpcPage() {
  const { t } = useTranslation("web");
  const { npcId } = useParams({ from: "/app/npcs/$npcId" });
  const npc = useNpcGet(npcId);
  if (npc.data === undefined && npc.error !== null) {
    return (
      <DetailFallback
        error={npc.error}
        icon={Bot}
        goneTitle={t("npcs.detail.gone")}
        goneDescription={t("npcs.detail.goneDescription")}
        failedTitle={t("npcs.detail.failed")}
        back={
          <Link to="/npcs" className="font-label text-accent-text">
            {t("npcs.detail.back")}
          </Link>
        }
      />
    );
  }
  const view = npc.data?.data;
  // A new key when the data arrives, so the form starts from it rather than from the placeholder.
  return (
    <Loading when={view === undefined}>
      <NpcEditor
        key={view === undefined ? "loading" : `loaded:${view.id}`}
        view={view ?? placeholderNpc}
      />
    </Loading>
  );
}

function NpcEditor({ view }: { view: NpcView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const client = useQueryClient();
  const canWrite = useCan("npcs:write");
  const groupIds = useBackendGroupIds();
  const update = useNpcUpdate();
  const outcome = useDetailSave(getNpcGetQueryKey(view.id));
  const remove = useDeleteNpc(() => void navigate({ to: "/npcs" }));
  const { form, changes, saving } = useViewSettingsForm({
    view,
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({ id: base.id, data: settings }),
    onSaved: (answer) => {
      void invalidatePaths(client, [getNpcListQueryKey()[0]]);
      outcome.onSaved(answer);
    },
    onFailed: outcome.onFailed,
  });
  const look = useStore(form.store, (state) => ({
    title: state.values.settings.title,
    subtitle: state.values.settings.subtitle,
  }));

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={
          <Value>
            {t("npcs.detail.title", { group: view.settings.groupId })}
          </Value>
        }
        actions={
          <MoreMenu
            groups={[
              canWrite
                ? [
                    {
                      label: t("npcs.delete.action"),
                      icon: Trash2,
                      danger: true,
                      onSelect: () => remove.ask(view.id),
                    },
                  ]
                : [],
            ]}
          />
        }
      />
      <EditorForm
        form={form}
        canWrite={canWrite}
        changes={changes}
        saving={saving}
        aside={
          <>
            <Card title={t("permissions.look.title")}>
              <NpcLook title={look.title} subtitle={look.subtitle} />
            </Card>
            <Card title={t("npcs.detail.identity")}>
              <CopyField value={view.id} label={t("npcs.detail.id")} />
            </Card>
          </>
        }
      >
        <NpcFields form={form} fields="settings" groupIds={groupIds} />
      </EditorForm>
      {remove.dialog}
    </div>
  );
}
