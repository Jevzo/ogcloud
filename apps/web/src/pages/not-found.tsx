import { EmptyState } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import { Compass } from "lucide-react";
import { useTranslation } from "react-i18next";

export function NotFound() {
  const { t } = useTranslation("web");
  return (
    <div className="grid min-h-[60vh] place-items-center">
      <EmptyState
        icon={Compass}
        title={t("errors.notFound")}
        description=""
        action={
          <Link to="/" className="font-label text-accent hover:underline">
            {t("errors.back")}
          </Link>
        }
      />
    </div>
  );
}
