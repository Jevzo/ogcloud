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
import { type ReactNode, useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ActionDialog } from "../../action-dialog.tsx";
import { loadFailure, useFailureToast } from "../../errors.ts";
import { useFormat } from "../../format.ts";
import { LoadFailed } from "../../load-failed.tsx";
import { usePendingAction } from "../../pending-action.ts";
import { useMe } from "../../permissions.ts";
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
        <span className="flex items-center gap-2 font-label">{title}</span>
        <span className="text-meta text-text-3">{detail}</span>
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

/** Starts an authenticator for the user: a QR code and its secret, confirmed with a first code. */
function TotpSetupDialog({ open, onClose }: CodeDialogProps) {
  const { t } = useTranslation("web");
  const failed = useFailureToast();
  const [enrollment, setEnrollment] = useState<TotpEnrollment | null>(null);
  const forget = useCallback(() => setEnrollment(null), []);
  const { code, setCode, close, finish } = useCodeDialog(onClose, forget);
  const start = useMeStartTotp();
  const confirm = useMeConfirmTotp();
  const { mutate: begin } = start;
  // Each opening asks the API for a new secret; nothing is enrolled until a code confirms it.
  useEffect(() => {
    if (!open) return;
    begin(undefined, {
      onSuccess: (answer) => setEnrollment(answer.data),
      onError: (error) => {
        failed(error);
        close();
      },
    });
  }, [open, begin, failed, close]);
  return (
    <ActionDialog
      open={open}
      onOpenChange={(next) => (next ? undefined : close())}
      title={t("account.totp.setUpTitle")}
      description={t("account.totp.setUpDescription")}
      submitLabel={t("account.totp.confirm")}
      disabled={code.length !== CODE_LENGTH || enrollment === null}
      busy={confirm.isPending}
      onSubmit={() =>
        confirm.mutate(
          { data: { code } },
          {
            onSuccess: () => finish(t("account.totp.added")),
            onError: failed,
          },
        )
      }
    >
      {enrollment === null ? (
        <div aria-busy="true" className="h-56" />
      ) : (
        <div className="flex flex-col gap-3">
          <div className="self-center rounded-control bg-white p-3">
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
  const format = useFormat();
  const now = Date.now();
  const passkeys = useMeListPasskeys();
  const pending = usePendingAction<PasskeyAction, PasskeyView>();
  const list = passkeys.data?.data ?? [];
  const failure = loadFailure(passkeys);
  return (
    <>
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
            detail={t("account.passkeys.detail", {
              added: format.ago(new Date(passkey.createdAt), now),
              used: format.ago(new Date(passkey.lastUsedAt), now),
            })}
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
    </>
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
      <Field label={t("account.password.current")}>
        {(control) => (
          <Input
            {...control}
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        )}
      </Field>
    </ActionDialog>
  );
}
