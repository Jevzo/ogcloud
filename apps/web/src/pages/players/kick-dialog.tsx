import { usePlayerTransfer } from "@ogcloud/api-client/react";
import {
  ChatPreview,
  Field,
  PreviewFrame,
  TagTextField,
  useToast,
} from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { ActionDialog, useOnOpen } from "../../action-dialog.tsx";
import { useFailureToast } from "../../errors.ts";
import { textProblem, useTextPreview } from "../../texts.ts";

interface KickDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  player: { uuid: string; name: string };
}

/** Disconnects a player with a message in the tag format (D-0206); they can join again. */
export function KickDialog({ open, onOpenChange, player }: KickDialogProps) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const [message, setMessage] = useState("");
  useOnOpen(open, () => {
    setMessage("");
  });
  const preview = useTextPreview("KICK_MESSAGE", message);
  const problems = preview.data?.errors ?? [];
  const first = problems[0];
  const kick = usePlayerTransfer();

  const submit = () =>
    kick.mutate(
      { uuid: player.uuid, data: { kickMessage: message } },
      {
        onSuccess: () => {
          toast({ message: t("players.kick.done", { name: player.name }) });
          setMessage("");
          onOpenChange(false);
        },
        onError: failed,
      },
    );

  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("players.kick.title", { name: player.name })}
      description={t("players.kick.description")}
      submitLabel={t("players.kick.submit")}
      onSubmit={submit}
      disabled={message.trim() === "" || problems.length > 0}
      busy={kick.isPending}
      kind="danger"
    >
      <Field
        label={t("players.kick.message")}
        error={first === undefined ? undefined : textProblem(t, first)}
      >
        {(control) => (
          <TagTextField
            {...control}
            value={message}
            onChange={setMessage}
            problems={problems}
            placeholders={preview.data?.placeholders ?? []}
            recentColours={[]}
          />
        )}
      </Field>
      {preview.data?.rendering == null || message === "" ? null : (
        <PreviewFrame label={t("players.kick.preview")}>
          <ChatPreview lines={[preview.data.rendering.modern]} />
        </PreviewFrame>
      )}
    </ActionDialog>
  );
}
