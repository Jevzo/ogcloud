import { ApiError } from "@ogcloud/api-client";
import { useToast } from "@ogcloud/ui";
import { useCallback } from "react";
import { useTranslation } from "react-i18next";

/** Says why an action failed, in the API's own words when it sent a ProblemDetail. */
export function useFailureToast(): (error: unknown) => void {
  const { t } = useTranslation("web");
  const toast = useToast();
  return useCallback(
    (error: unknown) => {
      const detail =
        error instanceof ApiError
          ? (error.problem?.detail ?? error.problem?.title)
          : undefined;
      toast({ message: detail ?? t("errors.action") });
    },
    [t, toast],
  );
}
