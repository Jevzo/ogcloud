import type { MeView } from "@ogcloud/api-client";
import type { DensityName } from "@ogcloud/tokens";
import { Menu, useToast } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { ChevronsUpDown, LogOut, UserRound } from "lucide-react";
import { useTranslation } from "react-i18next";
import { type ThemeChoice, usePreferences } from "../preferences.ts";
import { useSession } from "../session-context.ts";

interface AccountMenuProps {
  me: MeView;
  collapsed: boolean;
}

export function AccountMenu({ me, collapsed }: AccountMenuProps) {
  const { t } = useTranslation("web");
  const { preferences, change: onPreferences } = usePreferences();
  const session = useSession();
  const toast = useToast();
  const navigate = useNavigate();
  const initials = me.username.slice(0, 2).toUpperCase();

  const signOut = async () => {
    if (session === null) return;
    try {
      window.location.assign(await session.signOut());
    } catch (error) {
      console.warn("Signing out failed", error);
      toast({ message: t("shell.signOutFailed") });
    }
  };

  return (
    <Menu
      side="top"
      align="start"
      header={
        <span className="flex flex-col">
          <span className="font-label">{me.username}</span>
          <span className="text-meta text-text-3">{me.email}</span>
        </span>
      }
      groups={[
        {
          label: t("shell.theme.label"),
          value: preferences.theme,
          options: (["system", "light", "dark"] as const).map((value) => ({
            value,
            label: t(`shell.theme.${value}`),
          })),
          onValueChange: (theme) =>
            onPreferences({ ...preferences, theme: theme as ThemeChoice }),
        },
        {
          label: t("shell.density.label"),
          value: preferences.density,
          options: (["compact", "comfortable"] as const).map((value) => ({
            value,
            label: t(`shell.density.${value}`),
          })),
          onValueChange: (density) =>
            onPreferences({ ...preferences, density: density as DensityName }),
        },
        [
          {
            label: t("shell.account"),
            icon: UserRound,
            onSelect: () => void navigate({ to: "/account" }),
          },
          {
            label: t("shell.signOut"),
            icon: LogOut,
            onSelect: () => void signOut(),
          },
        ],
      ]}
      trigger={
        <button
          type="button"
          aria-label={collapsed ? me.username : undefined}
          className="flex w-full cursor-pointer items-center gap-2.5 rounded-control p-1.5 text-left hover:bg-surface-3"
        >
          <span
            aria-hidden="true"
            className="grid size-7.5 shrink-0 place-items-center rounded-full bg-accent-soft font-heavy text-accent-text text-meta"
          >
            {initials}
          </span>
          {collapsed ? null : (
            <>
              <span className="flex min-w-0 flex-1 flex-col leading-tight">
                <span className="truncate font-label">{me.username}</span>
                <span className="truncate text-meta text-text-3">
                  {me.roleId}
                </span>
              </span>
              <ChevronsUpDown
                aria-hidden="true"
                size={15}
                className="text-text-3"
              />
            </>
          )}
        </button>
      }
    />
  );
}
