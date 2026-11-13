import type { SearchResult } from "@ogcloud/api-client";
import { useSearchFind } from "@ogcloud/api-client/react";
import { useNavigate } from "@tanstack/react-router";
import { Command } from "cmdk";
import { Layers, Search, Server, UserRound } from "lucide-react";
import { Dialog } from "radix-ui";
import { type ReactNode, useDeferredValue, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ALL_NAV, allowed, type NavItem } from "../navigation.ts";
import { readRecent, remember, type Target, targetKey } from "./recent.ts";

const SEARCH_FROM_CHARACTERS = 2;

// What each kind of search result needs before ⌘K offers it (D-0092).
const READ_PERMISSION: Record<Exclude<Target["kind"], "page">, string> = {
  group: "groups:read",
  server: "servers:read",
  player: "players:read",
};

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  permissions: readonly string[];
}

/**
 * ⌘K: what the user opened lately, every page they may open, and servers, players and groups from
 * `/api/v1/search`.
 */
export function CommandPalette({
  open,
  onOpenChange,
  permissions,
}: CommandPaletteProps) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const typed = query.trim();
  const deferred = useDeferredValue(typed);
  const searchable = deferred.length >= SEARCH_FROM_CHARACTERS;
  const search = useSearchFind(
    { q: deferred },
    { query: { enabled: open && searchable } },
  );
  const navItems = allowed(ALL_NAV, permissions);
  const may = (target: Target) =>
    target.kind === "page"
      ? navItems.some((item) => item.path === target.path)
      : permissions.includes(READ_PERMISSION[target.kind]);
  // Five entries at most, so reading them on each render while open costs nothing.
  const recent = open && typed === "" ? readRecent() : [];
  const pages = navItems
    .filter((item) =>
      t(`nav.${item.label}`).toLowerCase().includes(typed.toLowerCase()),
    )
    .map((item): Target => ({ kind: "page", path: item.path }));
  const found = searchable ? targetsOf(search.data?.data) : [];
  const searching =
    typed.length >= SEARCH_FROM_CHARACTERS &&
    (deferred !== typed || search.isFetching);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        onOpenChange(!open);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onOpenChange]);

  // Closes the palette, remembers the pick, then goes where it leads.
  const go = (target: Target) => {
    onOpenChange(false);
    setQuery("");
    remember(target);
    void navigateTo(navigate, target);
  };
  const sections: [heading: string, targets: Target[]][] = [
    [t("palette.recent"), recent.filter(may)],
    [t("palette.pages"), pages],
    [
      t("palette.groups"),
      found.filter((target) => target.kind === "group").filter(may),
    ],
    [
      t("palette.servers"),
      found.filter((target) => target.kind === "server").filter(may),
    ],
    [
      t("palette.players"),
      found.filter((target) => target.kind === "player").filter(may),
    ],
  ];

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-scrim data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed top-[12vh] left-1/2 z-50 w-[min(36rem,calc(100vw-2rem))] -translate-x-1/2 glass overflow-hidden rounded-popover border border-border shadow-overlay data-[state=closed]:animate-palette-out data-[state=open]:animate-palette-in"
        >
          <Dialog.Title className="sr-only">
            {t("palette.placeholder")}
          </Dialog.Title>
          <Command shouldFilter={false} loop>
            <div className="flex items-center gap-2.5 border-border-subtle border-b px-4">
              <Search aria-hidden="true" size={16} className="text-text-3" />
              <Command.Input
                value={query}
                onValueChange={setQuery}
                placeholder={t("palette.placeholder")}
                className="h-12 flex-1 bg-transparent text-body outline-none placeholder:text-text-3"
              />
            </div>
            <Command.List className="max-h-[60vh] overflow-y-auto p-1.5">
              {searching ? (
                <Command.Loading>
                  <div className="px-3 py-2 text-small text-text-3">
                    {t("palette.searching")}
                  </div>
                </Command.Loading>
              ) : (
                <Command.Empty className="px-3 py-6 text-center text-small text-text-3">
                  {t("palette.noResults")}
                </Command.Empty>
              )}
              {sections.map(([heading, targets]) =>
                targets.length === 0 ? null : (
                  <Section key={heading} heading={heading}>
                    {targets.map((target) => (
                      <TargetItem
                        key={targetKey(target)}
                        target={target}
                        // A recent page is also among the pages; cmdk selects by value, so each needs its own.
                        value={`${heading}:${targetKey(target)}`}
                        navItems={navItems}
                        onSelect={() => go(target)}
                      />
                    ))}
                  </Section>
                ),
              )}
            </Command.List>
          </Command>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function targetsOf(results: SearchResult | undefined): Target[] {
  if (results === undefined) return [];
  return [
    ...(results.groups ?? []).map((id): Target => ({ kind: "group", id })),
    ...(results.servers ?? []).map(
      (server): Target => ({
        kind: "server",
        id: server.id,
        name: server.displayName,
        groupId: server.groupId,
      }),
    ),
    ...(results.players ?? []).map(
      (player): Target => ({
        kind: "player",
        uuid: player.uuid,
        name: player.name,
      }),
    ),
  ];
}

function navigateTo(
  navigate: ReturnType<typeof useNavigate>,
  target: Target,
): Promise<void> {
  switch (target.kind) {
    case "page":
      return navigate({ to: target.path });
    case "group":
      return navigate({
        to: "/groups/$groupId",
        params: { groupId: target.id },
      });
    case "server":
      return navigate({
        to: "/servers/$serverId",
        params: { serverId: target.id },
      });
    case "player":
      return navigate({
        to: "/players/$playerId",
        params: { playerId: target.uuid },
      });
  }
}

const ICON = { size: 15, strokeWidth: 1.5 } as const;

function TargetItem({
  target,
  value,
  navItems,
  onSelect,
}: {
  target: Target;
  value: string;
  navItems: readonly NavItem[];
  onSelect: () => void;
}) {
  const { t } = useTranslation("web");
  switch (target.kind) {
    case "page": {
      const item = navItems.find((nav) => nav.path === target.path);
      return item === undefined ? null : (
        <Item value={value} onSelect={onSelect} icon={<item.icon {...ICON} />}>
          {t(`nav.${item.label}`)}
        </Item>
      );
    }
    case "group":
      return (
        <Item value={value} onSelect={onSelect} icon={<Layers {...ICON} />}>
          {target.id}
        </Item>
      );
    case "server":
      return (
        <Item value={value} onSelect={onSelect} icon={<Server {...ICON} />}>
          <span className="min-w-0 flex-1 truncate font-mono text-mono">
            {target.name}
          </span>
          <span className="text-meta text-text-3">{target.groupId}</span>
        </Item>
      );
    case "player":
      return (
        <Item value={value} onSelect={onSelect} icon={<UserRound {...ICON} />}>
          {target.name}
        </Item>
      );
  }
}

function Section({
  heading,
  children,
}: {
  heading: string;
  children: ReactNode;
}) {
  return (
    <Command.Group
      heading={heading}
      className="[&_[cmdk-group-heading]]:px-2.5 [&_[cmdk-group-heading]]:pt-2 [&_[cmdk-group-heading]]:pb-1 [&_[cmdk-group-heading]]:font-label [&_[cmdk-group-heading]]:text-meta [&_[cmdk-group-heading]]:text-text-3"
    >
      {children}
    </Command.Group>
  );
}

function Item({
  value,
  onSelect,
  icon,
  children,
}: {
  value: string;
  onSelect: () => void;
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <Command.Item
      value={value}
      onSelect={onSelect}
      className="flex h-9 cursor-pointer items-center gap-2.5 rounded-control px-2.5 text-text-2 data-[selected=true]:bg-text/7 data-[selected=true]:text-text"
    >
      <span aria-hidden="true" className="shrink-0">
        {icon}
      </span>
      {children}
    </Command.Item>
  );
}
