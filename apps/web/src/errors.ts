import { ApiError } from "@ogcloud/api-client";
import { type TableFailure, useToast } from "@ogcloud/ui";
import { useCallback } from "react";
import { useTranslation } from "react-i18next";

/** The API's own words for a failure, when it sent a ProblemDetail. */
export function problemOf(error: unknown): string | undefined {
  return error instanceof ApiError
    ? (error.problem?.detail ?? error.problem?.title)
    : undefined;
}

/** Why something failed, as plainly as the error tells. */
export function messageOf(error: unknown): string {
  return (
    problemOf(error) ?? (error instanceof Error ? error.message : String(error))
  );
}

/** A list's load failure as the data table shows it, or none while the list loaded. */
export function loadFailure(list: {
  error: unknown;
  refetch: () => unknown;
}): TableFailure | undefined {
  if (list.error === null || list.error === undefined) return undefined;
  return { message: messageOf(list.error), retry: () => void list.refetch() };
}

/** Says why an action failed, in the API's own words when it sent a ProblemDetail, and logs the cause. */
export function useFailureToast(): (error: unknown) => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  return useCallback(
    (error: unknown) => {
      console.error("An action failed", error);
      toast({ message: problemOf(error) ?? t("errors.action") });
    },
    [t, toast],
  );
}
