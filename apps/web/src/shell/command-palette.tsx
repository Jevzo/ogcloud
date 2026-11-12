import type { SearchResult } from "@ogcloud/api-client";
import { useSearchFind } from "@ogcloud/api-client/react";
import { cn } from "@ogcloud/ui";
import { useNavigate } from "@tanstack/react-router";
import { Command } from "cmdk";
import { Layers, Search, Server, UserRound } from "lucide-react";
import { Dialog } from "radix-ui";
import { type ReactNode, useDeferredValue, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ALL_NAV, allowed } from "../navigation.ts";
import { useCan } from "../permissions.ts";

const SEARCH_FROM_CHARACTERS = 2;

interface CommandPaletteProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  permissions: readonly string[];
}

/** ⌘K: every page the user may open, and servers, players and groups from `/api/v1/search`. */
export function CommandPalette({
  open,
  onOpenChange,
  permissions,
}: CommandPaletteProps) {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const deferred = useDeferredValue(query.trim());
  const search = useSearchFind(
    { q: deferred },
    { query: { enabled: open && deferred.length >= SEARCH_FROM_CHARACTERS } },
  );
  const results = search.data?.data;
  const pages = allowed(ALL_NAV, permissions).filter((item) =>
    t(`nav.${item.label}`).toLowerCase().includes(query.trim().toLowerCase()),
  );

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

  // Closes the palette, then goes where the picked item leads.
  const go = (navigation: () => Promise<void>) => {
    onOpenChange(false);
    setQuery("");
    void navigation();
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-scrim data-[state=closed]:animate-fade-out data-[state=open]:animate-fade-in" />
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed top-[12vh] left-1/2 z-50 w-[min(36rem,calc(100vw-2rem))] -translate-x-1/2 overflow-hidden rounded-popover border border-border bg-surface shadow-popover data-[state=closed]:animate-pop-out data-[state=open]:animate-pop-in"
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
              <Command.Empty className="px-3 py-6 text-center text-small text-text-3">
                {t("palette.noResults")}
              </Command.Empty>
              {pages.length === 0 ? null : (
                <Section heading={t("palette.pages")}>
                  {pages.map((item) => (
                    <Item
                      key={item.path}
                      value={`page:${item.path}`}
                      onSelect={() => go(() => navigate({ to: item.path }))}
                      icon={<item.icon size={15} strokeWidth={1.75} />}
                    >
                      {t(`nav.${item.label}`)}
                    </Item>
                  ))}
                </Section>
              )}
              <Results results={results} go={go} navigate={navigate} />
            </Command.List>
          </Command>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function Results({
  results,
  go,
  navigate,
}: {
  results: SearchResult | undefined;
  go: (navigation: () => Promise<void>) => void;
  navigate: ReturnType<typeof useNavigate>;
}) {
  const { t } = useTranslation("web");
  const groups = useCan("groups:read") ? (results?.groups ?? []) : [];
  return (
    <>
      {groups.length === 0 ? null : (
        <Section heading={t("palette.groups")}>
          {groups.map((group) => (
            <Item
              key={group}
              value={`group:${group}`}
              onSelect={() =>
                go(() =>
                  navigate({
                    to: "/groups/$groupId",
                    params: { groupId: group },
                  }),
                )
              }
              icon={<Layers size={15} strokeWidth={1.75} />}
            >
              {group}
            </Item>
          ))}
        </Section>
      )}
      {(results?.servers ?? []).length === 0 ? null : (
        <Section heading={t("palette.servers")}>
          {results?.servers?.map((server) => (
            <Item
              key={server.id}
              value={`server:${server.id}`}
              onSelect={() =>
                go(() =>
                  navigate({
                    to: "/servers/$serverId",
                    params: { serverId: server.id },
                  }),
                )
              }
              icon={<Server size={15} strokeWidth={1.75} />}
            >
              <span className="min-w-0 flex-1 truncate font-mono text-mono">
                {server.displayName}
              </span>
              <span className="text-meta text-text-3">{server.groupId}</span>
            </Item>
          ))}
        </Section>
      )}
      {(results?.players ?? []).length === 0 ? null : (
        <Section heading={t("palette.players")}>
          {results?.players?.map((player) => (
            <Item
              key={player.uuid}
              value={`player:${player.uuid}`}
              onSelect={() =>
                go(() =>
                  navigate({
                    to: "/players/$playerId",
                    params: { playerId: player.uuid },
                  }),
                )
              }
              icon={<UserRound size={15} strokeWidth={1.75} />}
            >
              {player.name}
            </Item>
          ))}
        </Section>
      )}
    </>
  );
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
      className={cn(
        "flex h-9 cursor-pointer items-center gap-2.5 rounded-control px-2.5 text-text-2 data-[selected=true]:bg-surface-3 data-[selected=true]:text-text",
      )}
    >
      <span aria-hidden="true" className="shrink-0">
        {icon}
      </span>
      {children}
    </Command.Item>
  );
}
