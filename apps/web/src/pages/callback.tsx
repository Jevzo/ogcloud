import { Button, Card } from "@ogcloud/ui";
import { useRouter } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { signIn } from "../auth.ts";
import { useSession } from "../session-context.ts";

/** Where the API's login pages send the browser back with a code (D-0212). */
export function Callback() {
  const { t } = useTranslation("web");
  const session = useSession();
  const router = useRouter();
  const started = useRef(false);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    // StrictMode runs effects twice in development; the code may be exchanged only once.
    if (session === null || started.current) return;
    started.current = true;
    session.completeSignIn(new URL(window.location.href)).then(
      (returnTo) => router.history.replace(returnTo),
      (error: unknown) =>
        setFailure(error instanceof Error ? error.message : String(error)),
    );
  }, [session, router]);

  return (
    <main className="grid min-h-screen place-items-center p-6">
      <Card className="flex max-w-md flex-col gap-3 p-6 text-center">
        {failure === null ? (
          <p className="text-text-2">{t("signIn.redirecting")}</p>
        ) : (
          <>
            <p>{t("signIn.failed", { detail: failure })}</p>
            {session === null ? null : (
              <Button
                kind="primary"
                onClick={() => void signIn(session)}
                className="self-center"
              >
                {t("signIn.retry")}
              </Button>
            )}
          </>
        )}
      </Card>
    </main>
  );
}
