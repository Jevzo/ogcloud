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
function useSaveFailure(): (error: unknown, reload: () => void) => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  return useCallback(
    (error: unknown, reload: () => void) =>
      statusOf(error) === HttpStatus.CONFLICT
        ? toast({
            message: t("editing.stale"),
            action: { label: t("editing.reload"), onPress: reload },
          })
        : failed(error),
    [t, toast, failed],
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
  /** `showLatest` drops the edits for the newer state, which a stale save's Reload asks for. */
  onFailed: (error: unknown, showLatest: () => void) => void;
}

/** A detail page's save outcomes: it shows the API's answer at once, and reads again after a failed or stale save. */
export function useDetailSave<T>(queryKey: QueryKey): SaveOutcome<T> {
  const client = useQueryClient();
  const failed = useSaveFailure();
  return {
    onSaved: useSaved<T>(queryKey),
    onFailed: (error, showLatest) =>
      failed(error, () => {
        showLatest();
        void client.invalidateQueries({ queryKey });
      }),
  };
}

/** The save outcomes of a page that reads its subject from a list under `path`: the list reads again either way. */
export function useListSave(path: string): SaveOutcome<unknown> {
  const client = useQueryClient();
  const failed = useSaveFailure();
  return {
    onSaved: useSavedInList(path),
    onFailed: (error, showLatest) =>
      failed(error, () => {
        showLatest();
        void invalidatePaths(client, [path]);
      }),
  };
}

/** The form and its side panel; on large monitors the panel grows so previews scale up (D-0231). */
export const editorColumns =
  "grid grid-cols-[minmax(0,var(--container-form))_minmax(var(--container-aside-min),var(--container-aside))] items-start gap-6 min-[1920px]:grid-cols-[minmax(0,var(--container-form))_minmax(var(--container-aside-min),var(--container-aside-wide))]";

/** The side panel stays in view below the top bar while the form scrolls. */
export const editorAside = "sticky top-18 flex min-w-0 flex-col gap-6";

/**
 * An editor or create page, as wide as its form and side panel, so the header's actions sit over what they act on
 * rather than at the window's far edge (D-0231).
 */
export function EditorPage({ children }: { children: ReactNode }) {
  return (
    <div className="flex max-w-[calc(var(--container-form)+var(--container-aside)+--spacing(6))] flex-col gap-6 min-[1920px]:max-w-[calc(var(--container-form)+var(--container-aside-wide)+--spacing(6))]">
      {children}
    </div>
  );
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
    <div className={editorColumns}>
      <form
        className="flex min-w-0 flex-col gap-6"
        onSubmit={(event) => {
          event.preventDefault();
          save();
        }}
      >
        <fieldset
          disabled={!canWrite}
          className="m-0 flex min-w-0 flex-col gap-6 border-0 p-0"
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
      <div className={editorAside}>{aside}</div>
    </div>
  );
}

/**
 * A create page's actions in a footer that sticks to the bottom like the save bar, so they never sit a screen away:
 * Cancel, then the action itself, last (D-0210, D-0231).
 */
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
    <div className="sticky bottom-4 z-30 flex h-14 items-center justify-end gap-2 rounded-popover border border-border bg-floating px-3 shadow-popover">
      <Button kind="secondary" onClick={onCancel}>
        {t("editing.cancel")}
      </Button>
      <Button kind="primary" type="submit" loading={submitting}>
        {submitLabel}
      </Button>
    </div>
  );
}
