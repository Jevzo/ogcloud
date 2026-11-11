import type { NpcSettings, NpcView } from "@ogcloud/api-client";
import {
  getNpcGetQueryKey,
  getNpcListQueryKey,
  useNpcGet,
  useNpcUpdate,
} from "@ogcloud/api-client/react";
import { NpcUpdateBody } from "@ogcloud/api-client/zod";
import { Card, CopyField, PageHeader } from "@ogcloud/ui";
import { useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { Bot, Trash2 } from "lucide-react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { DetailFallback } from "../../detail-fallback.tsx";
import { EditorForm, useSaved, useSaveFailure } from "../../editing.tsx";
import { contractSchema, useSettingsForm } from "../../form.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useCan } from "../../permissions.ts";
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
  if (npc.data === undefined) {
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
  // NPCs carry no version, so their saved settings decide when the form starts over.
  return <NpcEditor key={npc.data.data.id} view={npc.data.data} />;
}

function NpcEditor({ view }: { view: NpcView }) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const client = useQueryClient();
  const canWrite = useCan("npcs:write");
  const groupIds = useBackendGroupIds();
  const update = useNpcUpdate();
  const saved = useSaved(getNpcGetQueryKey(view.id));
  const saveFailed = useSaveFailure(() =>
    client.invalidateQueries({ queryKey: getNpcGetQueryKey(view.id) }),
  );
  const remove = useDeleteNpc(() => void navigate({ to: "/npcs" }));
  const { form, changes, saving } = useSettingsForm({
    view,
    settingsOf: (npc) => npc.settings,
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({ id: base.id, data: settings }),
    onSaved: (answer) => {
      void invalidatePaths(client, [getNpcListQueryKey()[0]]);
      saved(answer);
    },
    onFailed: saveFailed,
  });
  const look = useStore(form.store, (state) => ({
    title: state.values.settings.title,
    subtitle: state.values.settings.subtitle,
  }));

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("npcs.detail.title", { group: view.settings.groupId })}
        description={t("npcs.detail.description", {
          world: view.settings.location.world,
        })}
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
