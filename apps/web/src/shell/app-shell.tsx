import { useMeGet } from "@ogcloud/api-client/react";
import {
  Loading,
  PageHeader,
  type ToastTone,
  useToast,
  Value,
} from "@ogcloud/ui";
import { Outlet, useLocation } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertsContext, alertMessage, useAlertList } from "../alerts.ts";
import { type Alert, useLiveEvents } from "../live.ts";
import { navItemOf, PRIMARY_PERMISSIONS } from "../navigation.ts";
import { MeContext } from "../permissions.ts";
import { placeholderMe } from "../placeholders.ts";
import {
  applyPreferences,
  type Preferences,
  PreferencesContext,
  readPreferences,
  writePreferences,
} from "../preferences.ts";
import { useSession } from "../session-context.ts";
import { AccountMenu } from "./account-menu.tsx";
import { CommandPalette } from "./command-palette.tsx";
import { Header } from "./header.tsx";
import { LinkRequired } from "./link-required.tsx";
import { ShellFailure } from "./shell-failure.tsx";
import { Sidebar } from "./sidebar.tsx";

/** Everything behind sign-in: the sidebar, the header with search and live state, and the page. */
const ALERT_TONES: Record<Alert["severity"], ToastTone> = {
  INFO: "info",
  WARNING: "warning",
  CRITICAL: "danger",
};

export function AppShell() {
  const { t } = useTranslation("web");
  const session = useSession();
  const toast = useToast();
  const me = useMeGet();
  const { pathname } = useLocation();
  const [preferences, setPreferences] = useState<Preferences>(readPreferences);
  const [searching, setSearching] = useState(false);

  const alerts = useAlertList(Date.now);
  const onAlert = useCallback(
    (alert: Alert) => {
      alerts.add(alert);
      toast({
        message: alertMessage(t, alert),
        tone: ALERT_TONES[alert.severity],
      });
    },
    [t, toast, alerts.add],
  );
  const live = useLiveEvents(session !== null, onAlert);
  const changePreferences = useCallback((next: Preferences) => {
    setPreferences(next);
    writePreferences(next);
    applyPreferences(next);
  }, []);
  const preferenceState = useMemo(
    () => ({ preferences, change: changePreferences }),
    [preferences, changePreferences],
  );

  if (me.isError && me.data === undefined) {
    return <ShellFailure error={me.error} onRetry={() => void me.refetch()} />;
  }
  // Until the profile is in, the shell shows itself as skeletons; pages wait, since what they may load depends on it.
  const loading = me.data === undefined;
  const profile = me.data?.data ?? placeholderMe;
  if (!loading && profile.permissions.length === 0) {
    return <LinkRequired me={profile} />;
  }
  const section = navItemOf(pathname);

  return (
    <AlertsContext.Provider value={alerts}>
      <MeContext.Provider value={profile}>
        <PreferencesContext.Provider value={preferenceState}>
          <div className="flex min-h-screen">
            <Loading when={loading}>
              <Sidebar
                permissions={
                  loading ? PRIMARY_PERMISSIONS : profile.permissions
                }
                collapsed={preferences.sidebarCollapsed}
                onToggle={() =>
                  changePreferences({
                    ...preferences,
                    sidebarCollapsed: !preferences.sidebarCollapsed,
                  })
                }
                account={
                  <AccountMenu
                    me={profile}
                    collapsed={preferences.sidebarCollapsed}
                  />
                }
              />
            </Loading>
            <div className="page-ground flex min-w-0 flex-1 flex-col">
              <Header live={live} onSearch={() => setSearching(true)} />
              <motion.main
                key={pathname}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex-1 px-6 pt-5 pb-8"
              >
                {loading ? (
                  <Loading when>
                    <PageHeader
                      title={
                        // A list's name is known now; a detail page's subject is not.
                        section?.path === pathname ? (
                          t(`nav.${section.label}`)
                        ) : (
                          <Value>{placeholderMe.username}</Value>
                        )
                      }
                    />
                  </Loading>
                ) : (
                  <Outlet />
                )}
              </motion.main>
            </div>
          </div>
          <CommandPalette
            open={searching}
            onOpenChange={setSearching}
            permissions={profile.permissions}
          />
        </PreferencesContext.Provider>
      </MeContext.Provider>
    </AlertsContext.Provider>
  );
}
