import type { NpcSettings } from "@ogcloud/api-client";
import { getNpcListQueryKey, useNpcCreate } from "@ogcloud/api-client/react";
import { NpcCreateBody } from "@ogcloud/api-client/zod";
import { Card, PageHeader, useToast } from "@ogcloud/ui";
import { useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import {
  CreateActions,
  EditorPage,
  editorAside,
  editorColumns,
} from "../../editing.tsx";
import { useFailureToast } from "../../errors.ts";
import { contractSchema, useAppForm } from "../../form.tsx";
import { invalidatePaths } from "../../queries.ts";
import { useBackendGroupIds } from "../groups/group-ids.ts";
import { NpcFields } from "./npc-fields.tsx";
import { NpcLook } from "./npc-look.tsx";

const CreateForm = contractSchema<{ settings: NpcSettings }>(
  z.object({ settings: NpcCreateBody }),
);
// Where NPCs usually stand: a lobby's spawn; most are placed in game with /ogcloud npc instead (D-0129).
const SPAWN_HEIGHT = 64;
const NEW_NPC: NpcSettings = {
  groupId: "",
  location: {
    world: "world",
    x: 0.5,
    y: SPAWN_HEIGHT,
    z: 0.5,
    yaw: 0,
    pitch: 0,
  },
  title: "",
  subtitle: "",
  skin: null,
  lookAtPlayers: true,
  leftClick: null,
  rightClick: null,
};

/** A new NPC from the dashboard, for when its place is known; it appears on every server of its group. */
export function NpcCreatePage() {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const navigate = useNavigate();
  const client = useQueryClient();
  const groupIds = useBackendGroupIds();
  const create = useNpcCreate({
    mutation: {
      onSettled: () => invalidatePaths(client, [getNpcListQueryKey()[0]]),
    },
  });
  const form = useAppForm({
    defaultValues: { settings: NEW_NPC },
    validators: { onChange: CreateForm },
    onSubmit: ({ value }) =>
      create.mutateAsync({ data: value.settings }).then((answer) => {
        toast({ message: t("npcs.create.done") });
        void navigate({
          to: "/npcs/$npcId",
          params: { npcId: answer.data.id },
        });
      }, failed),
  });
  const submitting = useStore(form.store, (state) => state.isSubmitting);

  return (
    <EditorPage>
      <PageHeader title={t("npcs.create.title")} />
      <form
        className={editorColumns}
        onSubmit={(event) => {
          event.preventDefault();
          void form.handleSubmit();
        }}
      >
        <div className="flex min-w-0 flex-col gap-6">
          <NpcFields form={form} fields="settings" groupIds={groupIds} />
          <CreateActions
            onCancel={() => void navigate({ to: "/npcs" })}
            submitLabel={t("npcs.create.submit")}
            submitting={submitting}
          />
        </div>
        <div className={editorAside}>
          <Card title={t("permissions.look.title")}>
            <form.Subscribe
              selector={(state) => [
                state.values.settings.title,
                state.values.settings.subtitle,
              ]}
            >
              {([title = "", subtitle = ""]) => (
                <NpcLook title={title} subtitle={subtitle} />
              )}
            </form.Subscribe>
          </Card>
        </div>
      </form>
    </EditorPage>
  );
}
