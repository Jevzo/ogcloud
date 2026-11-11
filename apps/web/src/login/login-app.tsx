import type { LoginCsrf, LoginState, TotpSetup } from "@ogcloud/api-client";
import { Button, CopyField, Field, Input, Note } from "@ogcloud/ui";
import {
  ArrowLeft,
  Fingerprint,
  KeyRound,
  Smartphone,
  TriangleAlert,
} from "lucide-react";
import { type ReactNode, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { BrandedPage } from "../branded-page.tsx";
import { QrCode } from "../qr-code.tsx";
import {
  type PasskeyProblem,
  passkeyProblemOf,
  registerPasskey,
  signInWithPasskey,
} from "./webauthn.ts";

/** Where the state and the authenticator secret come from: the API, or fixtures in mocks mode. */
export interface LoginSource {
  state: () => Promise<LoginState>;
  totpSetup: () => Promise<TotpSetup>;
}

const CONTINUE = "/login/mfa/continue";
// The account page sends users here to add a passkey, and gets them back (D-0218).
const ADD_PASSKEY = "/login/passkeys/new";
const ACCOUNT = "/account";

/** The branded sign-in (D-0212): plain form posts to Spring's endpoints, and WebAuthn for passkeys. */
export function LoginApp({ source }: { source: LoginSource }) {
  const { t } = useTranslation("login");
  const [state, setState] = useState<LoginState | null>(null);
  const [failed, setFailed] = useState(false);
  const rejected = new URLSearchParams(window.location.search).has("error");
  const adding = window.location.pathname === ADD_PASSKEY;

  useEffect(() => {
    source.state().then(setState, (error: unknown) => {
      console.warn("Could not load the login state", error);
      setFailed(true);
    });
  }, [source]);

  useEffect(() => {
    if (state?.step === "DONE" && !adding) window.location.replace(CONTINUE);
  }, [state, adding]);

  return (
    <BrandedPage>
      {failed ? (
        <Note tone="danger" icon={TriangleAlert}>
          {t("unavailable")}
        </Note>
      ) : state === null || (state.step === "DONE" && !adding) ? (
        <div aria-busy="true" className="h-40" />
      ) : state.step === "DONE" ? (
        <AddPasskey csrf={state.csrf} />
      ) : state.step === "CREDENTIALS" ? (
        <Credentials csrf={state.csrf} rejected={rejected} />
      ) : state.step === "TOTP" ? (
        <TotpCode csrf={state.csrf} rejected={rejected} />
      ) : state.step === "PASSKEY" ? (
        <PasskeyFactor csrf={state.csrf} />
      ) : (
        <Enrolment csrf={state.csrf} rejected={rejected} source={source} />
      )}
    </BrandedPage>
  );
}

function Heading({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="mb-5">
      <h1 className="text-title">{title}</h1>
      <p className="mt-1 text-text-2">{description}</p>
    </div>
  );
}

function CsrfField({ csrf }: { csrf: LoginCsrf }) {
  return <input type="hidden" name={csrf.parameterName} value={csrf.token} />;
}

function Actions({ children }: { children: ReactNode }) {
  return (
    <div className="mt-2 flex flex-wrap justify-end gap-2">{children}</div>
  );
}

function Problem({ children }: { children: ReactNode }) {
  return (
    <Note tone="danger" icon={TriangleAlert}>
      {children}
    </Note>
  );
}

function usePasskey(task: () => Promise<void>) {
  const { t } = useTranslation("login");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<PasskeyProblem | null>(null);
  const run = () => {
    setBusy(true);
    setProblem(null);
    task().catch((error: unknown) => {
      console.error("A passkey step failed", error);
      setProblem(passkeyProblemOf(error));
      setBusy(false);
    });
  };
  return {
    busy,
    problem: problem === null ? null : t(`problems.${problem}`),
    run,
  };
}

function Credentials({
  csrf,
  rejected,
}: {
  csrf: LoginCsrf;
  rejected: boolean;
}) {
  const { t } = useTranslation("login");
  const passkey = usePasskey(async () =>
    window.location.assign(await signInWithPasskey(csrf)),
  );
  return (
    <form method="post" action="/login" className="flex flex-col gap-4">
      <Heading
        title={t("credentials.title")}
        description={t("credentials.description")}
      />
      {rejected ? <Problem>{t("credentials.error")}</Problem> : null}
      {passkey.problem === null ? null : (
        <Problem>{t("passkey.failed", { detail: passkey.problem })}</Problem>
      )}
      <Field label={t("credentials.username")}>
        {(control) => (
          <Input
            {...control}
            name="username"
            autoComplete="username webauthn"
            required
            autoFocus
          />
        )}
      </Field>
      <Field label={t("credentials.password")}>
        {(control) => (
          <Input
            {...control}
            name="password"
            type="password"
            autoComplete="current-password"
            required
          />
        )}
      </Field>
      <CsrfField csrf={csrf} />
      <Actions>
        <Button icon={KeyRound} loading={passkey.busy} onClick={passkey.run}>
          {t("credentials.passkey")}
        </Button>
        <Button type="submit" kind="primary">
          {t("credentials.submit")}
        </Button>
      </Actions>
    </form>
  );
}

function CodeForm({
  action,
  csrf,
  label,
  submit,
  secondary,
}: {
  action: string;
  csrf: LoginCsrf;
  label: string;
  submit: string;
  secondary?: ReactNode;
}) {
  return (
    <form method="post" action={action} className="flex flex-col gap-4">
      <Field label={label}>
        {(control) => (
          <Input
            {...control}
            name="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            pattern="[0-9]*"
            required
            autoFocus
            mono
          />
        )}
      </Field>
      <CsrfField csrf={csrf} />
      <div className="mt-2 flex items-center gap-2">
        {secondary}
        <span className="flex-1" />
        <Button type="submit" kind="primary">
          {submit}
        </Button>
      </div>
    </form>
  );
}

function TotpCode({ csrf, rejected }: { csrf: LoginCsrf; rejected: boolean }) {
  const { t } = useTranslation("login");
  return (
    <>
      <Heading title={t("totp.title")} description={t("totp.description")} />
      {rejected ? <Problem>{t("totp.error")}</Problem> : null}
      <CodeForm
        action="/login/mfa/totp"
        csrf={csrf}
        label={t("totp.code")}
        submit={t("totp.submit")}
      />
    </>
  );
}

function PasskeyFactor({ csrf }: { csrf: LoginCsrf }) {
  const { t } = useTranslation("login");
  const passkey = usePasskey(async () =>
    window.location.assign(await signInWithPasskey(csrf)),
  );
  return (
    <>
      <Heading
        title={t("passkey.title")}
        description={t("passkey.description")}
      />
      {passkey.problem === null ? null : (
        <Problem>{t("passkey.failed", { detail: passkey.problem })}</Problem>
      )}
      <Actions>
        <Button
          kind="primary"
          icon={Fingerprint}
          loading={passkey.busy}
          onClick={passkey.run}
        >
          {t("passkey.use")}
        </Button>
      </Actions>
    </>
  );
}

function Enrolment({
  csrf,
  rejected,
  source,
}: {
  csrf: LoginCsrf;
  rejected: boolean;
  source: LoginSource;
}) {
  const { t } = useTranslation("login");
  // A refused code comes back with ?error, so the authenticator choice is reopened.
  const [choice, setChoice] = useState<"app" | "passkey" | null>(
    rejected ? "app" : null,
  );
  const back = (
    <Button kind="ghost" icon={ArrowLeft} onClick={() => setChoice(null)}>
      {t("enrol.back")}
    </Button>
  );
  return (
    <>
      <Heading title={t("enrol.title")} description={t("enrol.description")} />
      {choice === null ? (
        <div className="flex flex-col gap-2">
          <Choice
            icon={Smartphone}
            title={t("enrol.app")}
            description={t("enrol.appDescription")}
            onPress={() => setChoice("app")}
          />
          <Choice
            icon={Fingerprint}
            title={t("enrol.passkey")}
            description={t("enrol.passkeyDescription")}
            onPress={() => setChoice("passkey")}
          />
        </div>
      ) : choice === "app" ? (
        <AuthenticatorSetup
          csrf={csrf}
          rejected={rejected}
          source={source}
          back={back}
        />
      ) : (
        <PasskeySetup
          csrf={csrf}
          back={back}
          // With a passkey, a password login still owes a passkey sign-in (D-0097), so it signs in at once.
          onRegistered={async () =>
            window.location.assign(await signInWithPasskey(csrf))
          }
        />
      )}
    </>
  );
}

function Choice({
  icon: Icon,
  title,
  description,
  onPress,
}: {
  icon: typeof Smartphone;
  title: string;
  description: string;
  onPress: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onPress}
      className="flex cursor-pointer items-start gap-3 rounded-control border border-border bg-surface p-3 text-left shadow-raised hover:bg-surface-2"
    >
      <span
        aria-hidden="true"
        className="grid size-9 shrink-0 place-items-center rounded-control bg-accent-soft text-accent-text"
      >
        <Icon size={18} strokeWidth={1.75} />
      </span>
      <span className="flex flex-col">
        <span className="font-label">{title}</span>
        <span className="text-small text-text-2">{description}</span>
      </span>
    </button>
  );
}

function AuthenticatorSetup({
  csrf,
  rejected,
  source,
  back,
}: {
  csrf: LoginCsrf;
  rejected: boolean;
  source: LoginSource;
  back: ReactNode;
}) {
  const { t } = useTranslation("login");
  const [setup, setSetup] = useState<TotpSetup | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    source.totpSetup().then(setSetup, (error: unknown) => {
      console.warn("Could not start the authenticator setup", error);
      setFailed(true);
    });
  }, [source]);
  if (failed) return <Problem>{t("unavailable")}</Problem>;
  if (setup === null) return <div aria-busy="true" className="h-60" />;
  return (
    <div className="flex flex-col gap-4">
      {rejected ? <Problem>{t("enrol.error")}</Problem> : null}
      <p className="text-text-2">{t("enrol.scan")}</p>
      <div className="flex justify-center">
        <QrCode value={setup.uri} label={t("enrol.scan")} />
      </div>
      <div className="flex flex-col gap-1.5">
        <span className="font-label">{t("enrol.secret")}</span>
        <CopyField value={setup.secret} label={t("enrol.secret")} />
      </div>
      <CodeForm
        action="/login/mfa/totp-setup"
        csrf={csrf}
        label={t("enrol.code")}
        submit={t("enrol.confirm")}
        secondary={back}
      />
    </div>
  );
}

/** A signed-in user adds a passkey from their account page, then goes back to it (D-0218). */
function AddPasskey({ csrf }: { csrf: LoginCsrf }) {
  const { t } = useTranslation("login");
  return (
    <>
      <Heading title={t("add.title")} description={t("add.description")} />
      <PasskeySetup
        csrf={csrf}
        back={
          <Button kind="ghost" onClick={() => window.location.assign(ACCOUNT)}>
            {t("add.back")}
          </Button>
        }
        onRegistered={async () => window.location.assign(ACCOUNT)}
      />
    </>
  );
}

function PasskeySetup({
  csrf,
  back,
  onRegistered,
}: {
  csrf: LoginCsrf;
  back: ReactNode;
  onRegistered: () => Promise<void>;
}) {
  const { t } = useTranslation("login");
  const [label, setLabel] = useState("");
  const passkey = usePasskey(async () => {
    await registerPasskey(csrf, label.trim());
    await onRegistered();
  });
  return (
    <form
      className="flex flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault();
        passkey.run();
      }}
    >
      {passkey.problem === null ? null : (
        <Problem>{t("enrol.failed", { detail: passkey.problem })}</Problem>
      )}
      <Field label={t("enrol.label")} hint={t("enrol.labelHint")}>
        {(control) => (
          <Input
            {...control}
            value={label}
            onChange={(event) => setLabel(event.target.value)}
            required
            autoFocus
          />
        )}
      </Field>
      <div className="flex items-center gap-2">
        {back}
        <span className="flex-1" />
        <Button
          type="submit"
          kind="primary"
          icon={Fingerprint}
          loading={passkey.busy}
          disabled={label.trim() === ""}
        >
          {t("enrol.register")}
        </Button>
      </div>
    </form>
  );
}
