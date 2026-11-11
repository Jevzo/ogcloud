import { ApiError } from "@ogcloud/api-client";
import {
  type ApiConnection,
  ApiConnectionContext,
  useSetupComplete,
  useSetupStatus,
} from "@ogcloud/api-client/react";
import { Button, CopyField, Field, Input, Note } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import type { TFunction } from "i18next";
import {
  KeyRound,
  LogIn,
  RotateCw,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { HttpStatus, statusOf } from "../../http.ts";
import { useSession } from "../../session-context.ts";
import { isValidPassword } from "../users/new-password-field.tsx";
import {
  SetupFrame,
  type SetupStep,
  StepActions,
  StepHeading,
} from "./setup-frame.tsx";

// Nobody can sign in before the first owner exists, so these calls go out without a token (D-0094).
const ANONYMOUS: ApiConnection = {};
// Where the API Secret holds the token Infisical generated; the chart's default namespace.
const TOKEN_COMMAND =
  "kubectl -n ogcloud get secret ogcloud-api-config -o jsonpath='{.data.ogcloud\\.auth\\.setup-token}' | base64 -d";
/** Where the new owner continues once signed in: the EULA and the starter groups. */
export const WELCOME_PATH = "/welcome";

/** A fresh install's first visit: the setup token, the first owner, then signing in as them. */
export function SetupPage() {
  return (
    <ApiConnectionContext.Provider value={ANONYMOUS}>
      <Setup />
    </ApiConnectionContext.Provider>
  );
}

function Setup() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const status = useSetupStatus();
  const [step, setStep] = useState<SetupStep>("token");
  const [token, setToken] = useState("");
  const [tokenRefused, setTokenRefused] = useState(false);

  if (status.isPending) return <SetupFrame step="token">{null}</SetupFrame>;
  if (status.isError) {
    return (
      <SetupFrame step="token">
        <StepHeading
          title={t("setup.unreachable.title")}
          description={t("setup.unreachable.description")}
        />
        <StepActions>
          <Button
            kind="primary"
            icon={RotateCw}
            loading={status.isFetching}
            onClick={() => void status.refetch()}
          >
            {t("errors.retry")}
          </Button>
        </StepActions>
      </SetupFrame>
    );
  }
  if (status.data?.data.pending !== true && step !== "signIn") {
    return (
      <SetupFrame step="signIn">
        <StepHeading
          title={t("setup.done.title")}
          description={t("setup.done.description")}
        />
        <StepActions>
          <Button
            kind="primary"
            icon={LogIn}
            onClick={() => void navigate({ to: "/" })}
          >
            {t("setup.signIn.action")}
          </Button>
        </StepActions>
      </SetupFrame>
    );
  }
  return (
    <SetupFrame step={step}>
      {step === "token" ? (
        <TokenStep
          token={token}
          onToken={setToken}
          refused={tokenRefused}
          onNext={() => setStep("owner")}
        />
      ) : step === "owner" ? (
        <OwnerStep
          token={token}
          onBack={() => setStep("token")}
          onTokenRefused={() => {
            setTokenRefused(true);
            setStep("token");
          }}
          onCreated={() => setStep("signIn")}
        />
      ) : (
        <SignInStep />
      )}
    </SetupFrame>
  );
}

function TokenStep({
  token,
  onToken,
  refused,
  onNext,
}: {
  token: string;
  onToken: (token: string) => void;
  refused: boolean;
  onNext: () => void;
}) {
  const { t } = useTranslation("web");
  const next = (event: FormEvent) => {
    event.preventDefault();
    onNext();
  };
  return (
    <form className="flex flex-col gap-4" onSubmit={next}>
      <StepHeading
        title={t("setup.token.title")}
        description={t("setup.token.description")}
      />
      {refused ? (
        <Note tone="danger" icon={TriangleAlert}>
          {t("setup.token.refused")}
        </Note>
      ) : null}
      <div className="flex flex-col gap-1.5">
        <span className="font-label">{t("setup.token.where")}</span>
        <CopyField value={TOKEN_COMMAND} label={t("setup.token.command")} />
        <span className="text-small text-text-3">
          {t("setup.token.infisical")}
        </span>
      </div>
      <Field label={t("setup.token.label")}>
        {(control) => (
          <Input
            {...control}
            mono
            autoComplete="off"
            autoFocus
            value={token}
            onChange={(event) => onToken(event.target.value.trim())}
          />
        )}
      </Field>
      <StepActions>
        <Button
          type="submit"
          kind="primary"
          icon={KeyRound}
          disabled={token === ""}
        >
          {t("setup.next")}
        </Button>
      </StepActions>
    </form>
  );
}

function OwnerStep({
  token,
  onBack,
  onTokenRefused,
  onCreated,
}: {
  token: string;
  onBack: () => void;
  onTokenRefused: () => void;
  onCreated: () => void;
}) {
  const { t } = useTranslation("web");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [repeated, setRepeated] = useState("");
  const complete = useSetupComplete();
  const mismatch = repeated !== "" && repeated !== password;
  const ready =
    username.trim() !== "" &&
    email.includes("@") &&
    isValidPassword(password) &&
    repeated === password;
  const problem = ownerProblem(complete.error, t);

  const create = (event: FormEvent) => {
    event.preventDefault();
    complete.mutate(
      {
        data: {
          setupToken: token,
          username: username.trim(),
          email: email.trim(),
          password,
        },
      },
      {
        onSuccess: onCreated,
        onError: (error) => {
          if (statusOf(error) === HttpStatus.FORBIDDEN) onTokenRefused();
        },
      },
    );
  };

  return (
    <form className="flex flex-col gap-4" onSubmit={create}>
      <StepHeading
        title={t("setup.owner.title")}
        description={t("setup.owner.description")}
      />
      {problem === null ? null : (
        <Note tone="danger" icon={TriangleAlert}>
          {problem}
        </Note>
      )}
      <div className="grid grid-cols-2 gap-4">
        <Field label={t("users.form.username")}>
          {(control) => (
            <Input
              {...control}
              autoComplete="username"
              autoFocus
              value={username}
              onChange={(event) => setUsername(event.target.value)}
            />
          )}
        </Field>
        <Field label={t("users.form.email")}>
          {(control) => (
            <Input
              {...control}
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          )}
        </Field>
        <Field
          label={t("account.password.new")}
          hint={t("users.password.hint")}
        >
          {(control) => (
            <Input
              {...control}
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
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
      <StepActions>
        <Button kind="ghost" onClick={onBack}>
          {t("setup.back")}
        </Button>
        <Button
          type="submit"
          kind="primary"
          disabled={!ready}
          loading={complete.isPending}
        >
          {t("setup.owner.submit")}
        </Button>
      </StepActions>
    </form>
  );
}

/** Why creating the owner failed; a refused token sends the user back to that step instead. */
function ownerProblem(error: unknown, t: TFunction<"web">): string | null {
  if (!(error instanceof Error)) return null;
  if (!(error instanceof ApiError)) return t("errors.action");
  if (error.status === HttpStatus.FORBIDDEN) return null;
  if (error.status === HttpStatus.CONFLICT)
    return error.problem?.detail ?? t("setup.owner.taken");
  return error.problem?.detail ?? t("errors.action");
}

function SignInStep() {
  const { t } = useTranslation("web");
  const session = useSession();
  const failed = useFailureToast();
  return (
    <>
      <StepHeading
        title={t("setup.signIn.title")}
        description={t("setup.signIn.description")}
      />
      <Note tone="info" icon={ShieldCheck}>
        {t("setup.signIn.factor")}
      </Note>
      <StepActions>
        <Button
          kind="primary"
          icon={LogIn}
          disabled={session === null}
          onClick={() =>
            session === null
              ? undefined
              : void session
                  .beginSignIn(WELCOME_PATH)
                  .then((url) => window.location.assign(url), failed)
          }
        >
          {t("setup.signIn.action")}
        </Button>
      </StepActions>
    </>
  );
}
