import { useMeGet } from "@ogcloud/api-client/react";
import { Card, useToast } from "@ogcloud/ui";
import { Outlet, useLocation } from "@tanstack/react-router";
import { motion } from "motion/react";
import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertsContext, useAlertList } from "../alerts.ts";
import { type Alert, useLiveEvents } from "../live.ts";
import { MeContext } from "../permissions.ts";
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
import { Sidebar } from "./sidebar.tsx";

const CRASH_LOOP_PAUSED = "group.crash-loop-paused";

/** Everything behind sign-in: the sidebar, the header with search and live state, and the page. */
export function AppShell() {
  const { t } = useTranslation("web");
  const session = useSession();
  const toast = useToast();
  const me = useMeGet();
  const { pathname } = useLocation();
  const [preferences, setPreferences] = useState<Preferences>(readPreferences);
  const [collapsed, setCollapsed] = useState(false);
  const [searching, setSearching] = useState(false);

  const alerts = useAlertList(Date.now);
  const onAlert = useCallback(
    (alert: Alert) => {
      alerts.add(alert);
      toast({
        message:
          alert.kind === CRASH_LOOP_PAUSED
            ? t("alerts.crashLoopPaused", { subject: alert.subjectId })
            : t("alerts.other", { kind: alert.kind, subject: alert.subjectId }),
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

  if (me.isPending) return <main aria-busy="true" className="min-h-screen" />;
  if (me.isError) {
    return (
      <main className="grid min-h-screen place-items-center p-6">
        <Card className="max-w-md p-6">
          {t("errors.generic", {
            detail: me.error instanceof Error ? me.error.message : "",
          })}
        </Card>
      </main>
    );
  }
  const profile = me.data.data;
  if (profile.permissions.length === 0) return <LinkRequired me={profile} />;

  return (
    <AlertsContext.Provider value={alerts}>
      <MeContext.Provider value={profile}>
        <PreferencesContext.Provider value={preferenceState}>
          <div className="flex min-h-screen">
            <Sidebar
              permissions={profile.permissions}
              collapsed={collapsed}
              onToggle={() => setCollapsed((value) => !value)}
              account={<AccountMenu me={profile} collapsed={collapsed} />}
            />
            <div className="flex min-w-0 flex-1 flex-col">
              <Header live={live} onSearch={() => setSearching(true)} />
              <motion.main
                key={pathname}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex-1 px-4 pt-2 pb-6"
              >
                <Outlet />
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
