import {
  getPlayerGetQueryKey,
  getPlayerKnownQueryKey,
  getPlayerOnlineQueryKey,
  usePermissionGroupList,
  useRankSet,
} from "@ogcloud/api-client/react";
import {
  Button,
  DateTimePicker,
  Dialog,
  Field,
  PERMANENT,
  Select,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { invalidatePaths } from "../../queries.ts";

// The API takes v1's duration text (D-0102); whole seconds until the chosen time say the same.
const PERMANENT_DURATION = "-1";
const SECOND_MS = 1_000;

function durationUntil(until: Date | null, now: number): string {
  if (until === PERMANENT) return PERMANENT_DURATION;
  return `${Math.max(1, Math.round((until.getTime() - now) / SECOND_MS))}s`;
}

interface RankDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  player: { uuid: string; name: string; permissionGroupId: string };
}

/** Gives a player a permission group, permanently or until a time; online players get it at once. */
export function RankDialog({ open, onOpenChange, player }: RankDialogProps) {
  const { t } = useTranslation("web");
  const { t: ui } = useTranslation("ui");
  const toast = useToast();
  const failed = useFailureToast();
  const client = useQueryClient();
  const groups = usePermissionGroupList();
  const [group, setGroup] = useState(player.permissionGroupId);
  const [until, setUntil] = useState<Date | null>(PERMANENT);
  const setRank = useRankSet({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [
          getPlayerOnlineQueryKey()[0],
          getPlayerKnownQueryKey()[0],
          getPlayerGetQueryKey(player.uuid)[0],
        ]),
    },
  });

  const submit = () =>
    setRank.mutate(
      {
        uuid: player.uuid,
        data: {
          permissionGroupId: group,
          duration: durationUntil(until, Date.now()),
        },
      },
      {
        onSuccess: () => {
          toast({
            message: t("players.rank.done", { name: player.name, group }),
          });
          onOpenChange(false);
        },
        onError: failed,
      },
    );

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("players.rank.title", { name: player.name })}
      description={t("players.rank.description")}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
            {ui("actions.cancel")}
          </Button>
          <Button kind="primary" onClick={submit} loading={setRank.isPending}>
            {t("players.rank.submit")}
          </Button>
        </>
      }
    >
      <Field label={t("players.rank.group")}>
        {(control) => (
          <Select
            {...control}
            options={(groups.data?.data ?? []).map((candidate) => ({
              value: candidate.id,
              label: candidate.settings.name,
              hint: candidate.id,
            }))}
            value={group}
            onValueChange={setGroup}
          />
        )}
      </Field>
      <Field label={t("players.rank.until")} hint={t("players.rank.untilHint")}>
        {(control) => (
          <DateTimePicker
            {...control}
            value={until}
            onChange={setUntil}
            now={new Date()}
            allowPermanent
          />
        )}
      </Field>
    </Dialog>
  );
}
