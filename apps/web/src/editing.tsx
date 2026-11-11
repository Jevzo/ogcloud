import { ApiError } from "@ogcloud/api-client";
import { ConfirmDialog, SaveBar, useToast } from "@ogcloud/ui";
import { useBlocker } from "@tanstack/react-router";
import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "./errors.ts";

const CONFLICT = 409;

interface EditGuardProps {
  changes: number;
  saving: boolean;
  onDiscard: () => void;
  onSave: () => void;
}

/** An in-place editor's save bar, and a question before leaving with unsaved changes (D-0210). */
export function EditGuard({
  changes,
  saving,
  onDiscard,
  onSave,
}: EditGuardProps) {
  const { t } = useTranslation("web");
  const blocker = useBlocker({
    shouldBlockFn: () => changes > 0,
    enableBeforeUnload: () => changes > 0,
    withResolver: true,
  });
  return (
    <>
      <SaveBar
        changes={changes}
        saving={saving}
        onDiscard={onDiscard}
        onSave={onSave}
      />
      <ConfirmDialog
        open={blocker.status === "blocked"}
        onOpenChange={(open) => (open ? undefined : blocker.reset?.())}
        title={t("editing.leaveTitle")}
        description={t("editing.leaveDescription", { count: changes })}
        confirmLabel={t("editing.leave")}
        onConfirm={() => blocker.proceed?.()}
      />
    </>
  );
}

/**
 * Says a save failed. A stale version (409, D-0069) offers a reload that shows the newer state, keeping nothing of
 * the edits silently (D-0210).
 */
export function useSaveFailure(reload: () => void): (error: unknown) => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  return useCallback(
    (error: unknown) =>
      error instanceof ApiError && error.status === CONFLICT
        ? toast({
            message: t("editing.stale"),
            action: { label: t("editing.reload"), onPress: reload },
          })
        : failed(error),
    [t, toast, failed, reload],
  );
}
