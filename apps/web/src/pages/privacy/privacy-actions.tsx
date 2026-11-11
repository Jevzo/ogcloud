import {
  usePrivacyErasePlayer,
  usePrivacyEraseUser,
  usePrivacyExportPlayerHook,
  usePrivacyExportUserHook,
} from "@ogcloud/api-client/react";
import { ConfirmDialog, type MenuItem, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Download, Eraser } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { saveJson } from "../../files.ts";
import { usePendingAction } from "../../pending-action.ts";
import { useCan, useIsOwner } from "../../permissions.ts";

/** Someone whose stored data can be exported or erased (D-0049): a player by uuid, a dashboard user by id. */
export interface PrivacySubject {
  kind: "player" | "user";
  id: string;
  name: string;
  /** An owner's account, which only owners may erase (D-0094). */
  owner?: boolean;
}

/**
 * Exports as a JSON download and erasure behind the subject's typed name, since it cannot be undone (D-0107, D-0210).
 * [onErased] runs after an erasure, e.g. to leave the subject's page.
 */
export function usePrivacyActions(
  onErased: (subject: PrivacySubject) => void = () => undefined,
): {
  itemsFor: (subject: PrivacySubject) => MenuItem[];
  exportData: (subject: PrivacySubject) => void;
  askErase: (subject: PrivacySubject) => void;
  canExport: boolean;
  mayErase: (subject: PrivacySubject) => boolean;
  dialogs: ReactNode;
} {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const queries = useQueryClient();
  const canExport = useCan("privacy:export");
  const canErase = useCan("privacy:erase");
  const callerIsOwner = useIsOwner();
  const mayErase = (target: PrivacySubject) =>
    canErase && (target.owner !== true || callerIsOwner);
  const exportPlayer = usePrivacyExportPlayerHook();
  const exportUser = usePrivacyExportUserHook();
  const erasePlayer = usePrivacyErasePlayer();
  const eraseUser = usePrivacyEraseUser();
  const pending = usePendingAction<"erase", PrivacySubject>();

  const exportData = (subject: PrivacySubject) =>
    void (
      subject.kind === "player"
        ? exportPlayer(subject.id)
        : exportUser(subject.id)
    ).then((answer) => {
      saveJson(answer.data, `ogcloud-${subject.kind}-${subject.name}.json`);
      toast({ message: t("privacy.exported", { name: subject.name }) });
    }, failed);
  const erase = (subject: PrivacySubject) =>
    (subject.kind === "player"
      ? erasePlayer.mutateAsync({ uuid: subject.id })
      : eraseUser.mutateAsync({ id: subject.id })
    ).then(() => {
      toast({ message: t("privacy.erased", { name: subject.name }) });
      void queries.invalidateQueries();
      onErased(subject);
    }, failed);

  const subject = pending.target;
  return {
    itemsFor: (target) => [
      ...(canExport
        ? [
            {
              label: t("privacy.export"),
              icon: Download,
              onSelect: () => exportData(target),
            },
          ]
        : []),
      ...(mayErase(target)
        ? [
            {
              label: t("privacy.erase"),
              icon: Eraser,
              danger: true,
              onSelect: () => pending.open("erase", target),
            },
          ]
        : []),
    ],
    exportData,
    askErase: (target) => pending.open("erase", target),
    canExport,
    mayErase,
    dialogs: (
      <ConfirmDialog
        open={pending.isOpen("erase")}
        onOpenChange={pending.onOpenChange}
        title={t("privacy.eraseTitle", { name: subject?.name ?? "" })}
        description={
          subject?.kind === "user"
            ? t("privacy.eraseUserDescription")
            : t("privacy.erasePlayerDescription")
        }
        confirmLabel={t("privacy.erase")}
        typedName={subject?.name ?? ""}
        onConfirm={() => (subject === undefined ? undefined : erase(subject))}
      />
    ),
  };
}
