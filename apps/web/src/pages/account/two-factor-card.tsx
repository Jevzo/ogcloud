import type { PasskeyView, TotpEnrollment } from "@ogcloud/api-client";
import {
  getMeGetQueryKey,
  getMeListPasskeysQueryKey,
  useMeConfirmTotp,
  useMeListPasskeys,
  useMeRemovePasskey,
  useMeRemoveTotp,
  useMeRenamePasskey,
  useMeStartTotp,
} from "@ogcloud/api-client/react";
import { meRenamePasskeyBodyLabelMax } from "@ogcloud/api-client/zod";
import {
  Badge,
  Button,
  Card,
  CopyField,
  Field,
  Input,
  Loading,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import {
  CircleCheck,
  Fingerprint,
  Pencil,
  Plus,
  Smartphone,
  Trash2,
} from "lucide-react";
import { type ReactNode, useCallback, useState } from "react";
import { Trans, useTranslation } from "react-i18next";
import { ActionDialog } from "../../action-dialog.tsx";
import { Ago } from "../../ago.tsx";
import { loadFailure, useFailureToast } from "../../errors.ts";
import { LoadFailed } from "../../load-failed.tsx";
import { usePendingAction } from "../../pending-action.ts";
import { useMe } from "../../permissions.ts";
import { placeholderPasskey } from "../../placeholders.ts";
import { QrCode } from "../../qr-code.tsx";
import { CODE_LENGTH, CodeField } from "./code-field.tsx";

// The login page that adds a passkey for a full sign-in and comes back here (D-0218).
const ADD_PASSKEY = "/login/passkeys/new";

/** Second factors: an authenticator app and passkeys (D-0097); owners always keep one. */
export function TwoFactorCard() {
  const { t } = useTranslation("web");
  return (
    <Card
      title={t("account.factors.title")}
      actions={
        <Button
          size="sm"
          icon={Plus}
          onClick={() => window.location.assign(ADD_PASSKEY)}
        >
          {t("account.passkeys.add")}
        </Button>
      }
    >
      <div className="flex flex-col">
        <AuthenticatorRow />
        <PasskeyRows />
      </div>
    </Card>
  );
}

function FactorRow({
  icon: Icon,
  title,
  detail,
  children,
}: {
  icon: typeof Smartphone;
  title: ReactNode;
  detail: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex items-center gap-3 border-border-subtle border-t py-2.5 first:border-t-0 first:pt-0">
      <Icon aria-hidden="true" size={18} className="shrink-0 text-text-2" />
      <span className="flex min-w-0 flex-1 flex-col">
        <span
          data-skeleton=""
          className="flex items-center gap-2 self-start font-label"
        >
          {title}
        </span>
        <span data-skeleton="" className="self-start text-meta text-text-3">
          {detail}
        </span>
      </span>
      <span className="flex gap-1.5">{children}</span>
    </div>
  );
}

function AuthenticatorRow() {
  const { t } = useTranslation("web");
  const me = useMe();
  const [dialog, setDialog] = useState<"setup" | "remove" | null>(null);
  // Stable, since the setup dialog asks for a new secret whenever its close handler changes while open.
  const closeDialog = useCallback(() => setDialog(null), []);
  return (
    <FactorRow
      icon={Smartphone}
      title={
        <>
          {t("account.totp.title")}
          {me.totpEnabled ? (
            <Badge tone="success" icon={CircleCheck}>
              {t("account.totp.on")}
            </Badge>
          ) : null}
        </>
      }
      detail={
        me.totpEnabled
          ? t("account.totp.onDetail")
          : t("account.totp.offDetail")
      }
    >
      {me.totpEnabled ? (
        <Button size="sm" onClick={() => setDialog("remove")}>
          {t("account.totp.remove")}
        </Button>
      ) : (
        <Button size="sm" onClick={() => setDialog("setup")}>
          {t("account.totp.setUp")}
        </Button>
      )}
      <TotpSetupDialog open={dialog === "setup"} onClose={closeDialog} />
      <TotpRemoveDialog open={dialog === "remove"} onClose={closeDialog} />
    </FactorRow>
  );
}

interface CodeDialogProps {
  open: boolean;
  onClose: () => void;
}

/** What both authenticator dialogs keep: the code typed so far, and how they finish. */
function useCodeDialog(
  onClose: () => void,
  reset: () => void = () => undefined,
) {
  const toast = useToast();
  const queries = useQueryClient();
  const [code, setCode] = useState("");
  const close = useCallback(() => {
    setCode("");
    reset();
    onClose();
  }, [onClose, reset]);
  // The profile says whether an authenticator is on.
  const finish = (message: string) => {
    toast({ message });
    void queries.invalidateQueries({ queryKey: getMeGetQueryKey() });
    close();
  };
  return { code, setCode, close, finish };
}

/**
 * Starts an authenticator for the user: the current password first (D-0224), then a QR code and its secret, confirmed
 * with a first code.
 */
function TotpSetupDialog({ open, onClose }: CodeDialogProps) {
  const { t } = useTranslation("web");
  const failed = useFailureToast();
  const [password, setPassword] = useState("");
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null);
  const forget = useCallback(() => {
    setPassword("");
    setEnrollment(null);
  }, []);
  const { code, setCode, close, finish } = useCodeDialog(onClose, forget);
  const start = useMeStartTotp();
  const confirm = useMeConfirmTotp();
  // Each start makes a new secret; nothing is enrolled until a code confirms it.
  const begin = () =>
    start.mutate(
      { data: { password } },
      {
        onSuccess: (answer) => {
          setPassword("");
          setEnrollment(answer.data);
        },
        onError: failed,
      },
    );
  const confirmCode = () =>
    confirm.mutate(
      { data: { code } },
      {
        onSuccess: () => finish(t("account.totp.added")),
        onError: failed,
      },
    );
  return (
    <ActionDialog
      open={open}
      onOpenChange={(next) => (next ? undefined : close())}
      title={t("account.totp.setUpTitle")}
      description={
        enrollment === null
          ? t("account.totp.passwordDescription")
          : t("account.totp.setUpDescription")
      }
      submitLabel={
        enrollment === null
          ? t("account.totp.continue")
          : t("account.totp.confirm")
      }
      disabled={
        enrollment === null ? password === "" : code.length !== CODE_LENGTH
      }
      busy={enrollment === null ? start.isPending : confirm.isPending}
      onSubmit={enrollment === null ? begin : confirmCode}
    >
      {enrollment === null ? (
        <CurrentPasswordField value={password} onChange={setPassword} />
      ) : (
        <div className="flex flex-col gap-3">
          <div className="self-center rounded-control bg-qr-ground p-3">
            <QrCode value={enrollment.uri} label={t("account.totp.scan")} />
          </div>
          <CopyField
            value={enrollment.secret}
            label={t("account.totp.secret")}
          />
          <CodeField
            label={t("account.totp.code")}
            value={code}
            onChange={setCode}
          />
        </div>
      )}
    </ActionDialog>
  );
}

/** The caller's current password, which changes to their second factors need (D-0217, D-0224). */
function CurrentPasswordField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation("web");
  return (
    <Field label={t("account.password.current")}>
      {(control) => (
        <Input
          {...control}
          type="password"
          autoComplete="current-password"
          value={value}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
    </Field>
  );
}

/** Removing the authenticator needs a current code from it (D-0108). */
function TotpRemoveDialog({ open, onClose }: CodeDialogProps) {
  const { t } = useTranslation("web");
  const failed = useFailureToast();
  const { code, setCode, close, finish } = useCodeDialog(onClose);
  const remove = useMeRemoveTotp();
  return (
    <ActionDialog
      open={open}
      onOpenChange={(next) => (next ? undefined : close())}
      title={t("account.totp.removeTitle")}
      description={t("account.totp.removeDescription")}
      kind="danger"
      submitLabel={t("account.totp.remove")}
      disabled={code.length !== CODE_LENGTH}
      busy={remove.isPending}
      onSubmit={() =>
        remove.mutate(
          { data: { code } },
          {
            onSuccess: () => finish(t("account.totp.removed")),
            onError: failed,
          },
        )
      }
    >
      <CodeField
        label={t("account.totp.code")}
        value={code}
        onChange={setCode}
      />
    </ActionDialog>
  );
}

type PasskeyAction = "rename" | "remove";

function PasskeyRows() {
  const { t } = useTranslation("web");
  const passkeys = useMeListPasskeys();
  const pending = usePendingAction<PasskeyAction, PasskeyView>();
  const failure = loadFailure(passkeys);
  const loading = passkeys.data === undefined && failure === undefined;
  const list = passkeys.data?.data ?? (loading ? [placeholderPasskey] : []);
  return (
    <Loading when={loading}>
      {failure !== undefined && list.length === 0 ? (
        <LoadFailed failure={failure} />
      ) : list.length === 0 ? (
        <FactorRow
          icon={Fingerprint}
          title={t("account.passkeys.title")}
          detail={t("account.passkeys.none")}
        >
          {null}
        </FactorRow>
      ) : (
        list.map((passkey) => (
          <FactorRow
            key={passkey.id}
            icon={Fingerprint}
            title={passkey.label}
            detail={
              <Trans
                t={t}
                i18nKey="account.passkeys.detail"
                components={{
                  added: <Ago at={passkey.createdAt} className="" />,
                  used: <Ago at={passkey.lastUsedAt} className="" />,
                }}
              />
            }
          >
            <Button
              size="sm"
              kind="ghost"
              icon={Pencil}
              iconOnly
              onClick={() => pending.open("rename", passkey)}
            >
              {t("account.passkeys.rename")}
            </Button>
            <Button
              size="sm"
              kind="ghost"
              icon={Trash2}
              iconOnly
              onClick={() => pending.open("remove", passkey)}
            >
              {t("account.passkeys.remove")}
            </Button>
          </FactorRow>
        ))
      )}
      <RenamePasskeyDialog
        passkey={pending.target}
        open={pending.isOpen("rename")}
        onOpenChange={pending.onOpenChange}
      />
      <RemovePasskeyDialog
        passkey={pending.target}
        open={pending.isOpen("remove")}
        onOpenChange={pending.onOpenChange}
      />
    </Loading>
  );
}

function RenamePasskeyDialog({
  passkey,
  open,
  onOpenChange,
}: {
  passkey: PasskeyView | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("web");
  const failed = useFailureToast();
  const queries = useQueryClient();
  const [label, setLabel] = useState<string | null>(null);
  const rename = useMeRenamePasskey();
  const value = label ?? passkey?.label ?? "";
  const close = () => {
    setLabel(null);
    onOpenChange(false);
  };
  return (
    <ActionDialog
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title={t("account.passkeys.renameTitle")}
      description={t("account.passkeys.renameDescription")}
      submitLabel={t("account.passkeys.rename")}
      disabled={value.trim() === ""}
      busy={rename.isPending}
      onSubmit={() =>
        passkey === undefined
          ? undefined
          : rename.mutate(
              { id: passkey.id, data: { label: value.trim() } },
              {
                onSuccess: () => {
                  void queries.invalidateQueries({
                    queryKey: getMeListPasskeysQueryKey(),
                  });
                  close();
                },
                onError: failed,
              },
            )
      }
    >
      <Field label={t("account.passkeys.name")}>
        {(control) => (
          <Input
            {...control}
            value={value}
            onChange={(event) => setLabel(event.target.value)}
            maxLength={meRenamePasskeyBodyLabelMax}
          />
        )}
      </Field>
    </ActionDialog>
  );
}

/** Removing a passkey needs the password (D-0217). */
function RemovePasskeyDialog({
  passkey,
  open,
  onOpenChange,
}: {
  passkey: PasskeyView | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const queries = useQueryClient();
  const [password, setPassword] = useState("");
  const remove = useMeRemovePasskey();
  const close = () => {
    setPassword("");
    onOpenChange(false);
  };
  return (
    <ActionDialog
      open={open}
      onOpenChange={(next) => (next ? onOpenChange(true) : close())}
      title={t("account.passkeys.removeTitle", { name: passkey?.label ?? "" })}
      description={t("account.passkeys.removeDescription")}
      kind="danger"
      submitLabel={t("account.passkeys.remove")}
      disabled={password === ""}
      busy={remove.isPending}
      onSubmit={() =>
        passkey === undefined
          ? undefined
          : remove.mutate(
              { id: passkey.id, data: { password } },
              {
                onSuccess: () => {
                  toast({
                    message: t("account.passkeys.removed", {
                      name: passkey.label,
                    }),
                  });
                  void queries.invalidateQueries({
                    queryKey: getMeListPasskeysQueryKey(),
                  });
                  close();
                },
                onError: failed,
              },
            )
      }
    >
      <CurrentPasswordField value={password} onChange={setPassword} />
    </ActionDialog>
  );
}
