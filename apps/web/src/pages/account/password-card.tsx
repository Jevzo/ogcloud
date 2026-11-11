import { useMeChangePassword } from "@ogcloud/api-client/react";
import { Button, Card, Field, Input, Note, useToast } from "@ogcloud/ui";
import { LogOut } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { useSignOut } from "../../session-context.ts";
import { isValidPassword } from "../users/new-password-field.tsx";

/** Changes the user's own password; that ends every login, this one too, so the dashboard signs in again. */
export function PasswordCard() {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const signOut = useSignOut();
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeated, setRepeated] = useState("");
  const change = useMeChangePassword();
  const mismatch = repeated !== "" && repeated !== next;
  const ready = current !== "" && isValidPassword(next) && repeated === next;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    change.mutate(
      { data: { currentPassword: current, newPassword: next } },
      {
        onSuccess: () => {
          toast({ message: t("account.password.changed") });
          void signOut();
        },
        onError: failed,
      },
    );
  };

  return (
    <Card title={t("account.password.title")}>
      <form className="flex flex-col gap-3" onSubmit={submit}>
        <Field label={t("account.password.current")}>
          {(control) => (
            <Input
              {...control}
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(event) => setCurrent(event.target.value)}
            />
          )}
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field
            label={t("account.password.new")}
            hint={t("users.password.hint")}
          >
            {(control) => (
              <Input
                {...control}
                type="password"
                autoComplete="new-password"
                value={next}
                onChange={(event) => setNext(event.target.value)}
              />
            )}
          </Field>
          <Field
            label={t("account.password.repeat")}
            error={mismatch ? t("account.password.mismatch") : undefined}
          >
            {(control) => (
              <Input
                {...control}
                type="password"
                autoComplete="new-password"
                value={repeated}
                onChange={(event) => setRepeated(event.target.value)}
              />
            )}
          </Field>
        </div>
        <Note tone="info" icon={LogOut}>
          {t("account.password.signsOut")}
        </Note>
        <div className="flex justify-end">
          <Button
            type="submit"
            kind="primary"
            disabled={!ready}
            loading={change.isPending}
          >
            {t("account.password.submit")}
          </Button>
        </div>
      </form>
    </Card>
  );
}
