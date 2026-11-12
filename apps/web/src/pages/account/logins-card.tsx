import type { LoginView } from "@ogcloud/api-client";
import {
  getMeListLoginsQueryKey,
  useMeListLogins,
  useMeRevokeLogin,
} from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  Loading,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { AppWindow, LogOut, MonitorSmartphone } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { WEB_CLIENT } from "../../auth.ts";
import {
  loadFailure,
  useConfirmFailure,
  useFailureToast,
} from "../../errors.ts";
import { useFormat } from "../../format.ts";
import { LoadFailed } from "../../load-failed.tsx";
import { placeholderLogin } from "../../placeholders.ts";

// OgCloud's own apps by client id (D-0012, D-0153); any other client shows its id.
// Two rows, as a signed-in user has at least this device and often another.
const PLACEHOLDER_LOGINS = [placeholderLogin, placeholderLogin];

const APPS = {
  [WEB_CLIENT]: "web",
  "ogcloud-desktop": "desktop",
  "ogcloud-mobile": "mobile",
  "ogcloud-grafana": "grafana",
} as const;

const isApp = (clientId: string): clientId is keyof typeof APPS =>
  clientId in APPS;

/**
 * Where the user is signed in, this device marked (D-0221); signing one out also ends every sign-in session of the
 * user, so a device in the middle of signing in starts over (D-0213).
 */
export function LoginsCard() {
  const { t } = useTranslation("web");
  const format = useFormat();
  const toast = useToast();
  const failed = useFailureToast();
  const keepOpen = useConfirmFailure();
  const queries = useQueryClient();
  const logins = useMeListLogins();
  const revoke = useMeRevokeLogin();
  const [signingOutOthers, setSigningOutOthers] = useState(false);
  const failure = loadFailure(logins);
  const loading = logins.data === undefined && failure === undefined;
  const list = logins.data?.data ?? (loading ? PLACEHOLDER_LOGINS : []);
  const others = list.filter((login) => !login.current);
  const now = Date.now();
  const refresh = () =>
    queries.invalidateQueries({ queryKey: getMeListLoginsQueryKey() });
  const appOf = (login: LoginView) =>
    isApp(login.clientId)
      ? t(`account.logins.apps.${APPS[login.clientId]}`)
      : login.clientId;
  const ago = (at: string | null | undefined) =>
    at == null ? "" : format.ago(new Date(at), now);

  const signOut = (login: LoginView) =>
    revoke.mutateAsync({ id: login.id }).then(() => {
      toast({ message: t("account.logins.signedOut", { app: appOf(login) }) });
      void refresh();
    }, failed);

  return (
    <Card
      title={t("account.logins.title")}
      actions={
        others.length > 0 && !loading ? (
          <Button
            size="sm"
            icon={LogOut}
            onClick={() => setSigningOutOthers(true)}
          >
            {t("account.logins.signOutOthers")}
          </Button>
        ) : undefined
      }
    >
      {failure === undefined ? null : <LoadFailed failure={failure} />}
      <Loading when={loading}>
        <ul className="flex flex-col">
          {list.map((login) => (
            <li
              key={login.id}
              className="flex items-center gap-3 border-border-subtle border-t py-2.5 first:border-t-0 first:pt-0"
            >
              {login.clientId === WEB_CLIENT ? (
                <AppWindow
                  aria-hidden="true"
                  size={18}
                  className="shrink-0 text-text-2"
                />
              ) : (
                <MonitorSmartphone
                  aria-hidden="true"
                  size={18}
                  className="shrink-0 text-text-2"
                />
              )}
              <span className="flex min-w-0 flex-1 flex-col">
                <span
                  data-skeleton=""
                  className="flex items-center gap-2 self-start font-label"
                >
                  {appOf(login)}
                  {login.current ? (
                    <Badge tone="accent">
                      {t("account.logins.thisDevice")}
                    </Badge>
                  ) : null}
                </span>
                <span
                  data-skeleton=""
                  className="self-start text-meta text-text-3"
                >
                  {t("account.logins.detail", {
                    signedIn: ago(login.signedInAt),
                    active: ago(login.lastActiveAt),
                  })}
                </span>
              </span>
              {login.current ? null : (
                <Button size="sm" onClick={() => void signOut(login)}>
                  {t("account.logins.signOut")}
                </Button>
              )}
            </li>
          ))}
        </ul>
      </Loading>
      <ConfirmDialog
        open={signingOutOthers}
        onOpenChange={setSigningOutOthers}
        title={t("account.logins.signOutOthersTitle")}
        description={t("account.logins.signOutOthersDescription", {
          count: others.length,
        })}
        confirmLabel={t("account.logins.signOutOthers")}
        onConfirm={async () => {
          // Every device is tried, and the list shows what is left even when one sign-out keepOpen.
          const outcomes = await Promise.allSettled(
            others.map((login) => revoke.mutateAsync({ id: login.id })),
          );
          void refresh();
          const refused = outcomes.find(
            (outcome) => outcome.status === "rejected",
          );
          if (refused === undefined) {
            toast({ message: t("account.logins.othersSignedOut") });
          } else {
            keepOpen(refused.reason);
          }
        }}
      />
    </Card>
  );
}
