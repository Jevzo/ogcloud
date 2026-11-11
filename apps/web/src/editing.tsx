import { Button, ConfirmDialog, SaveBar, useToast } from "@ogcloud/ui";
import { type QueryKey, useQueryClient } from "@tanstack/react-query";
import { useBlocker } from "@tanstack/react-router";
import { type ReactNode, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "./errors.ts";
import { HttpStatus, statusOf } from "./http.ts";
import { invalidatePaths } from "./queries.ts";

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
function useSaveFailure(reload: () => void): (error: unknown) => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  return useCallback(
    (error: unknown) =>
      statusOf(error) === HttpStatus.CONFLICT
        ? toast({
            message: t("editing.stale"),
            action: { label: t("editing.reload"), onPress: reload },
          })
        : failed(error),
    [t, toast, failed, reload],
  );
}

/** After a save of something the page reads from a list: the list reloads, and the page says it saved. */
function useSavedInList(path: string): () => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  const client = useQueryClient();
  return useCallback(() => {
    void invalidatePaths(client, [path]);
    toast({ message: t("editing.saved") });
  }, [client, path, t, toast]);
}

/** After a save: the page shows what the API answered at once, and says it saved. */
function useSaved<T>(queryKey: QueryKey): (saved: T) => void {
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

interface SaveOutcome<T> {
  onSaved: (saved: T) => void;
  onFailed: (error: unknown) => void;
}

/** A detail page's save outcomes: it shows the API's answer at once, and reads again after a failed or stale save. */
export function useDetailSave<T>(queryKey: QueryKey): SaveOutcome<T> {
  const client = useQueryClient();
  return {
    onSaved: useSaved<T>(queryKey),
    onFailed: useSaveFailure(() => void client.invalidateQueries({ queryKey })),
  };
}

/** The save outcomes of a page that reads its subject from a list under `path`: the list reads again either way. */
export function useListSave(path: string): SaveOutcome<unknown> {
  const client = useQueryClient();
  return {
    onSaved: useSavedInList(path),
    onFailed: useSaveFailure(() => void invalidatePaths(client, [path])),
  };
}

interface EditorFormProps {
  form: { handleSubmit: () => Promise<void>; reset: () => void };
  canWrite: boolean;
  changes: number;
  saving: boolean;
  /** Beside the form on wide screens, such as live status or a preview (D-0210). */
  aside: ReactNode;
  children: ReactNode;
}

/**
 * A page's settings form, about 720 px wide with its side panel: read-only without the write permission, otherwise
 * with its save bar and leave guard.
 */
export function EditorForm({
  form,
  canWrite,
  changes,
  saving,
  aside,
  children,
}: EditorFormProps) {
  const save = () => void form.handleSubmit();
  return (
    <div className="grid grid-cols-[minmax(0,var(--container-form))_minmax(280px,440px)] items-start gap-4">
      <form
        className="flex min-w-0 flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault();
          save();
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
            onDiscard={() => form.reset()}
            onSave={save}
          />
        ) : null}
      </form>
      <div className="sticky top-16 flex min-w-0 flex-col gap-4">{aside}</div>
    </div>
  );
}

/** A create page's actions at the end of its form: Cancel, then the action itself, last (D-0210). */
export function CreateActions({
  onCancel,
  submitLabel,
  submitting,
}: {
  onCancel: () => void;
  submitLabel: string;
  submitting: boolean;
}) {
  const { t } = useTranslation("web");
  return (
    <div className="flex justify-end gap-2">
      <Button kind="secondary" onClick={onCancel}>
        {t("editing.cancel")}
      </Button>
      <Button kind="primary" type="submit" loading={submitting}>
        {submitLabel}
      </Button>
    </div>
  );
}
