import { useOverviewGet } from "@ogcloud/api-client/react";
import { Button, cn, Tooltip } from "@ogcloud/ui";
import { Link } from "@tanstack/react-router";
import { PanelLeft } from "lucide-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import mascot from "../assets/mascot-64.png";
import { useFormat } from "../format.ts";
import {
  ADMIN_NAV,
  allowed,
  type NavItem,
  PRIMARY_NAV,
} from "../navigation.ts";
import { useCan } from "../permissions.ts";

const COUNTS_REFRESH_MS = 10_000;

interface SidebarProps {
  permissions: readonly string[];
  collapsed: boolean;
  onToggle: () => void;
  account: ReactNode;
}

/** The floating navigation panel of the Layered direction (D-0216); collapsed, it shows icons with tooltips. */
export function Sidebar({
  permissions,
  collapsed,
  onToggle,
  account,
}: SidebarProps) {
  const { t } = useTranslation("web");
  const counts = useNavCounts();
  const admin = allowed(ADMIN_NAV, permissions);
  return (
    <aside
      aria-label="OgCloud"
      className={cn(
        "sticky top-2 m-2 flex h-[calc(100vh-1rem)] shrink-0 flex-col rounded-card border border-border-subtle bg-surface p-2.5 shadow-card",
        collapsed ? "w-15" : "w-sidebar",
      )}
    >
      <div
        className={cn(
          "mb-2.5 flex h-10 items-center gap-2.5",
          collapsed ? "flex-col justify-center" : "pr-1 pl-1.5",
        )}
      >
        {collapsed ? null : (
          <>
            <img src={mascot} alt="" className="size-7.5" />
            <span className="flex-1 font-heavy text-[17px] tracking-tight">
              OgCloud
            </span>
          </>
        )}
        <Button
          kind="ghost"
          size="sm"
          icon={PanelLeft}
          iconOnly
          onClick={onToggle}
        >
          {collapsed ? t("nav.expand") : t("nav.collapse")}
        </Button>
      </div>
      <nav className="flex flex-col gap-0.5">
        {allowed(PRIMARY_NAV, permissions).map((item) => (
          <NavLink
            key={item.path}
            item={item}
            collapsed={collapsed}
            count={counts[item.path]}
          />
        ))}
      </nav>
      {admin.length === 0 ? null : (
        <>
          {collapsed ? (
            <hr className="my-3 border-border-subtle" />
          ) : (
            <div className="px-2.5 pt-4.5 pb-1.5 font-label text-meta text-text-3">
              {t("nav.administration")}
            </div>
          )}
          <nav className="flex flex-col gap-0.5">
            {admin.map((item) => (
              <NavLink key={item.path} item={item} collapsed={collapsed} />
            ))}
          </nav>
        </>
      )}
      <div className="flex-1" />
      <div className="mt-3 border-border-subtle border-t pt-2">{account}</div>
    </aside>
  );
}

function NavLink({
  item,
  collapsed,
  count,
}: {
  item: NavItem;
  collapsed: boolean;
  count?: string | undefined;
}) {
  const { t } = useTranslation("web");
  const label = t(`nav.${item.label}`);
  const link = (
    <Link
      to={item.path}
      activeOptions={{ exact: item.path === "/" }}
      aria-label={collapsed ? label : undefined}
      className={cn(
        "group flex h-nav-item items-center gap-2.5 rounded-control px-2.5 text-text-2 transition-colors hover:bg-surface-3 hover:text-text",
        "aria-[current=page]:bg-accent-soft aria-[current=page]:font-label aria-[current=page]:text-accent-text",
        collapsed && "justify-center px-0",
      )}
    >
      <item.icon
        aria-hidden="true"
        size={16}
        strokeWidth={1.75}
        className="shrink-0 group-aria-[current=page]:text-accent"
      />
      {collapsed ? null : (
        <>
          <span className="flex-1 truncate">{label}</span>
          {count === undefined ? null : (
            <span className="text-meta text-text-3 tabular-nums">{count}</span>
          )}
        </>
      )}
    </Link>
  );
  return collapsed ? (
    <Tooltip content={label} side="right">
      {link}
    </Tooltip>
  ) : (
    link
  );
}

/** Live counts beside Servers and Players, for those who may read the overview. */
function useNavCounts(): Partial<Record<NavItem["path"], string>> {
  const format = useFormat();
  const enabled = useCan("overview:read");
  const overview = useOverviewGet({
    query: { enabled, refetchInterval: COUNTS_REFRESH_MS },
  });
  const data = overview.data?.data;
  if (data === undefined) return {};
  const servers = Object.values(data.serversByState).reduce(
    (sum, count) => sum + count,
    0,
  );
  return {
    "/servers": format.number(servers),
    "/players": format.number(data.playersOnline),
  };
}
