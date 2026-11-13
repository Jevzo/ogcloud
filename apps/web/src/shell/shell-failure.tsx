import { Button } from "@ogcloud/ui";
import { CloudOff, LogIn, RotateCw } from "lucide-react";
import { useTranslation } from "react-i18next";
import { signIn } from "../auth.ts";
import { BrandedPage } from "../branded-page.tsx";
import { messageOf } from "../errors.ts";
import { HttpStatus, statusOf } from "../http.ts";
import { useSession } from "../session-context.ts";

type Reason = "tooMany" | "unreachable" | "server" | "other";

function reasonOf(error: unknown): Reason {
  const status = statusOf(error);
  if (status === undefined) return "unreachable";
  if (status === HttpStatus.TOO_MANY_REQUESTS) return "tooMany";
  return status >= HttpStatus.SERVER_ERROR ? "server" : "other";
}

/** When the signed-in user's profile cannot load, the shell cannot draw: this says why in plain words, with a way on. */
export function ShellFailure({
  error,
  onRetry,
}: {
  error: unknown;
  onRetry: () => void;
}) {
  const { t } = useTranslation("web");
  const session = useSession();
  return (
    <BrandedPage>
      <div className="flex flex-col gap-3">
        <CloudOff
          aria-hidden="true"
          size={22}
          strokeWidth={1.5}
          className="text-text-2"
        />
        <h1 className="text-title">{t("shell.failure.title")}</h1>
        <p className="text-text-2">{t(`shell.failure.${reasonOf(error)}`)}</p>
        <p className="font-mono text-meta text-text-3">{messageOf(error)}</p>
        <div className="flex justify-end gap-2 pt-2">
          {session === null ? null : (
            <Button icon={LogIn} onClick={() => void signIn(session)}>
              {t("shell.failure.signIn")}
            </Button>
          )}
          <Button kind="primary" icon={RotateCw} onClick={onRetry}>
            {t("errors.retry")}
          </Button>
        </div>
      </div>
    </BrandedPage>
  );
}
