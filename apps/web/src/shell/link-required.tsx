import { ApiError, type MeView } from "@ogcloud/api-client";
import {
  useMeConfirmMinecraftLink,
  useMeRequestMinecraftLink,
} from "@ogcloud/api-client/react";
import { Button, Card, Field, Input, Note } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link2, RotateCcw } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import mascot from "../assets/mascot-256.png";
import { problemOf } from "../errors.ts";
import { useSession } from "../session-context.ts";

const BAD_REQUEST = 400;
const NOT_FOUND = 404;
const CONFLICT = 409;

/**
 * A user whose token carries no permissions: either the network requires a linked Minecraft account and this one
 * has none (D-0094, D-0106), or the role grants nothing.
 */
export function LinkRequired({ me }: { me: MeView }) {
  const { t } = useTranslation("web");
  return (
    <main className="grid min-h-screen place-items-center p-6">
      <Card className="flex w-full max-w-md flex-col gap-4 p-6">
        <img src={mascot} alt="" className="size-20 self-center" />
        {me.linkedPlayerUuid === null ? (
          <>
            <div className="text-center">
              <h1 className="text-title">{t("link.title")}</h1>
              <p className="mt-1 text-text-2">{t("link.description")}</p>
            </div>
            <LinkFlow />
          </>
        ) : (
          <div className="text-center">
            <h1 className="text-title">{t("link.noAccessTitle")}</h1>
            <p className="mt-1 text-text-2">{t("link.noAccess")}</p>
          </div>
        )}
      </Card>
    </main>
  );
}

/** Sends a code to the player in game and links the account once it is typed back (D-0106). */
export function LinkFlow() {
  const { t } = useTranslation("web");
  const session = useSession();
  const queries = useQueryClient();
  const [name, setName] = useState("");
  const [code, setCode] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const request = useMeRequestMinecraftLink();
  const confirm = useMeConfirmMinecraftLink();

  const send = (event: FormEvent) => {
    event.preventDefault();
    request.mutate(
      { data: { playerName: name.trim() } },
      { onSuccess: () => setSentTo(name.trim()) },
    );
  };
  const link = (event: FormEvent) => {
    event.preventDefault();
    confirm.mutate(
      { data: { code: code.trim() } },
      {
        // The next token carries the permissions the link opens (D-0106).
        onSuccess: () => {
          session?.forgetAccessToken();
          void queries.invalidateQueries();
        },
      },
    );
  };
  const requestError =
    request.error === null
      ? undefined
      : statusOf(request.error) === NOT_FOUND
        ? t("link.offline", { name: name.trim() })
        : (problemOf(request.error) ?? t("errors.action"));
  // A wrong or expired code answers 400 and is gone (D-0106); anything else is not the code's fault.
  const confirmError =
    confirm.error === null
      ? undefined
      : statusOf(confirm.error) === BAD_REQUEST
        ? t("link.wrongCode")
        : statusOf(confirm.error) === CONFLICT
          ? t("link.taken")
          : (problemOf(confirm.error) ?? t("errors.action"));

  return sentTo === null ? (
    <form onSubmit={send} className="flex flex-col gap-3">
      <Field label={t("link.playerName")} error={requestError}>
        {(control) => (
          <Input
            {...control}
            value={name}
            onChange={(event) => setName(event.target.value)}
            autoComplete="off"
          />
        )}
      </Field>
      <Button
        type="submit"
        kind="primary"
        loading={request.isPending}
        disabled={name.trim() === ""}
        className="self-end"
      >
        {t("link.send")}
      </Button>
    </form>
  ) : (
    <form onSubmit={link} className="flex flex-col gap-3">
      <Note tone="info" icon={Link2}>
        {t("link.sent", { name: sentTo })}
      </Note>
      <Field label={t("link.code")} error={confirmError}>
        {(control) => (
          <Input
            {...control}
            value={code}
            onChange={(event) => setCode(event.target.value)}
            inputMode="numeric"
            autoComplete="one-time-code"
            mono
          />
        )}
      </Field>
      {statusOf(confirm.error) === BAD_REQUEST ? (
        <Button
          kind="ghost"
          icon={RotateCcw}
          onClick={() => setSentTo(null)}
          className="self-start"
        >
          {t("link.send")}
        </Button>
      ) : null}
      <Button
        type="submit"
        kind="primary"
        loading={confirm.isPending}
        disabled={code.trim() === ""}
        className="self-end"
      >
        {t("link.confirm")}
      </Button>
    </form>
  );
}

function statusOf(error: unknown): number | undefined {
  return error instanceof ApiError ? error.status : undefined;
}
