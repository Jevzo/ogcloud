import { Button, Note, type TableFailure } from "@ogcloud/ui";
import { RotateCw, TriangleAlert } from "lucide-react";
import { useTranslation } from "react-i18next";

/** In place of a card's content that did not load: why, and a way to try again. */
export function LoadFailed({ failure }: { failure: TableFailure }) {
  const { t } = useTranslation("web");
  return (
    <Note
      tone="danger"
      icon={TriangleAlert}
      action={
        <Button size="sm" icon={RotateCw} onClick={failure.retry}>
          {t("errors.retry")}
        </Button>
      }
    >
      {t("errors.load", { detail: failure.message })}
    </Note>
  );
}
