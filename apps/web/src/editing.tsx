import { ApiError } from "@ogcloud/api-client";
import { ConfirmDialog, SaveBar, useToast } from "@ogcloud/ui";
import { type QueryKey, useQueryClient } from "@tanstack/react-query";
import { useBlocker } from "@tanstack/react-router";
import { type ReactNode, useCallback } from "react";
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

/** After a save: the page shows what the API answered at once, and says it saved. */
export function useSaved<T>(queryKey: QueryKey): (saved: T) => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  const client = useQueryClient();
  return useCallback(
    (saved: T) => {
      client.setQueryData(queryKey, saved);
      toast({ message: t("editing.saved") });
    },
    [client, queryKey, t, toast],
  );
}

interface EditorFormProps {
  canWrite: boolean;
  changes: number;
  saving: boolean;
  onSave: () => void;
  onDiscard: () => void;
  children: ReactNode;
}

/** A page's settings form: read-only without the write permission, otherwise with its save bar and leave guard. */
export function EditorForm({
  canWrite,
  changes,
  saving,
  onSave,
  onDiscard,
  children,
}: EditorFormProps) {
  return (
    <form
      className="flex min-w-0 flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSave();
      }}
    >
      <fieldset
        disabled={!canWrite}
        className="m-0 flex min-w-0 flex-col gap-4 border-0 p-0"
      >
        {children}
      </fieldset>
      {canWrite ? (
        <EditGuard
          changes={changes}
          saving={saving}
          onDiscard={onDiscard}
          onSave={onSave}
        />
      ) : null}
    </form>
  );
}
