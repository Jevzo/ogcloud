import type { MeView } from "@ogcloud/api-client";
import { cn, Menu } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { ChevronsUpDown, LogOut, UserRound } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Initials } from "../avatar.tsx";
import { useSignOut } from "../session-context.ts";
import { railFade } from "./sidebar.tsx";

interface AccountMenuProps {
  me: MeView;
  collapsed: boolean;
}

export function AccountMenu({ me, collapsed }: AccountMenuProps) {
  const { t } = useTranslation("web");
  const signOut = useSignOut();
  const navigate = useNavigate();

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
          className="flex w-full cursor-pointer items-center gap-2.5 rounded-control p-1.5 text-left hover:bg-surface"
        >
          <Initials name={me.username} className="size-7.5 text-meta" />
          {/* Faded rather than removed, so the sidebar's slide does not jump (D-0235). */}
          <span
            aria-hidden={collapsed}
            className={cn(
              "flex min-w-0 flex-1 items-center gap-2.5 whitespace-nowrap",
              railFade(collapsed),
            )}
          >
            <span className="flex min-w-0 flex-1 flex-col">
              <span data-skeleton="" className="truncate font-label">
                {me.username}
              </span>
              <span data-skeleton="" className="truncate text-meta text-text-3">
                {me.roleId}
              </span>
            </span>
            <ChevronsUpDown
              aria-hidden="true"
              size={15}
              className="shrink-0 text-text-3"
            />
          </span>
        </button>
      }
    />
  );
}
