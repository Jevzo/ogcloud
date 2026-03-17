import { Command as CommandPrimitive } from "cmdk";
import {
    ArrowUpRightIcon,
    Layers3Icon,
    LoaderCircleIcon,
    SearchIcon,
    ServerIcon,
    UsersIcon,
} from "lucide-react";
import { startTransition, useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";

import PlayerManagementModal from "@/components/PlayerManagementModal";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import {
    Command,
    CommandGroup,
    CommandItem,
    CommandList,
    CommandSeparator,
    CommandShortcut,
} from "@/components/ui/command";
import { InputGroup, InputGroupAddon } from "@/components/ui/input-group";
import { useGlobalSearchQuery } from "@/features/search/hooks/use-global-search-query";
import { useAccessToken } from "@/hooks/use-access-token";
import { getPlayerByUuid } from "@/lib/api";
import { formatDateTime } from "@/lib/server-display";
import type { PersistedPlayerRecord } from "@/types/player";

const SEARCH_RESULT_LIMIT = 6;

const getGroupStatusBadgeClassName = (maintenance: boolean) =>
    maintenance
        ? "border-amber-500/30 bg-amber-500/10 text-amber-300"
        : "border-emerald-500/30 bg-emerald-500/10 text-emerald-300";

const HeaderSearchCommand = () => {
    const navigate = useNavigate();
    const getAccessToken = useAccessToken();
    const rootRef = useRef<HTMLDivElement | null>(null);
    const inputRef = useRef<HTMLInputElement | null>(null);

    const [query, setQuery] = useState("");
    const [isOpen, setIsOpen] = useState(false);
    const [selectedPlayer, setSelectedPlayer] = useState<PersistedPlayerRecord | null>(null);
    const [loadingPlayerUuid, setLoadingPlayerUuid] = useState<string | null>(null);

    const { errorMessage, isSearching, results, totalResultCount, trimmedQuery } =
        useGlobalSearchQuery({
            limit: SEARCH_RESULT_LIMIT,
            query,
        });

    const closeSearch = useCallback(() => {
        setIsOpen(false);
    }, []);

    const resetSearchSurface = useCallback(() => {
        setIsOpen(false);
        setQuery("");
    }, []);

    useEffect(() => {
        const handlePointerDown = (event: MouseEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) {
                setIsOpen(false);
            }
        };

        const handleKeyboardShortcut = (event: KeyboardEvent) => {
            if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
                event.preventDefault();
                setIsOpen(true);
                inputRef.current?.focus();
            }

            if (event.key === "Escape") {
                setIsOpen(false);
            }
        };

        window.addEventListener("mousedown", handlePointerDown);
        window.addEventListener("keydown", handleKeyboardShortcut);

        return () => {
            window.removeEventListener("mousedown", handlePointerDown);
            window.removeEventListener("keydown", handleKeyboardShortcut);
        };
    }, []);

    const handleNavigate = useCallback(
        (targetPath: string) => {
            resetSearchSurface();
            startTransition(() => {
                navigate(targetPath);
            });
        },
        [navigate, resetSearchSurface],
    );

    const handleOpenPlayer = useCallback(
        async (playerUuid: string) => {
            setLoadingPlayerUuid(playerUuid);

            try {
                const accessToken = await getAccessToken();
                const player = await getPlayerByUuid(accessToken, playerUuid);

                setSelectedPlayer(player);
                resetSearchSurface();
            } catch (error) {
                toast.error(
                    error instanceof Error ? error.message : "Unable to load that player.",
                );
            } finally {
                setLoadingPlayerUuid(null);
            }
        },
        [getAccessToken, resetSearchSurface],
    );

    const shouldShowPanel = isOpen;
    const shouldShowServerSection = results.servers.length > 0;
    const shouldShowGroupSection = results.groups.length > 0;
    const shouldShowPlayerSection = results.players.length > 0;

    return (
        <>
            <div ref={rootRef} className="relative w-full">
                <Command
                    shouldFilter={false}
                    loop
                    className="overflow-visible border-0 bg-transparent p-0"
                >
                    <InputGroup className="h-10 rounded-2xl border-border/70 bg-background/75 shadow-none backdrop-blur-sm">
                        <InputGroupAddon className="text-muted-foreground">
                            {isSearching ? (
                                <LoaderCircleIcon className="size-4 animate-spin" />
                            ) : (
                                <SearchIcon className="size-4" />
                            )}
                        </InputGroupAddon>
                        <CommandPrimitive.Input
                            ref={inputRef}
                            value={query}
                            onValueChange={setQuery}
                            onFocus={() => setIsOpen(true)}
                            placeholder="Search instances, groups, or players..."
                            className="flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                        />
                        <InputGroupAddon
                            align="inline-end"
                            className="gap-1 text-[11px] text-muted-foreground/80"
                        >
                            <kbd className="rounded-md border border-border/80 bg-muted/40 px-1.5 py-0.5 font-sans text-[11px]">
                                Ctrl
                            </kbd>
                            <kbd className="rounded-md border border-border/80 bg-muted/40 px-1.5 py-0.5 font-sans text-[11px]">
                                K
                            </kbd>
                        </InputGroupAddon>
                    </InputGroup>

                    {shouldShowPanel ? (
                        <Card className="absolute inset-x-0 top-[calc(100%+0.75rem)] z-40 border border-border/70 bg-card/96 p-0 shadow-2xl shadow-black/25 backdrop-blur-xl">
                            <div className="flex items-center justify-between gap-3 border-b border-border/70 px-4 py-3">
                                <div>
                                    <div className="text-sm font-medium text-foreground">
                                        Search network
                                    </div>
                                    <div className="text-xs text-muted-foreground">
                                        {trimmedQuery
                                            ? isSearching
                                                ? `Searching for "${trimmedQuery}"...`
                                                : `${totalResultCount} result${totalResultCount === 1 ? "" : "s"} across servers, groups, and players`
                                            : "Type to search and press Enter to open the selected result"}
                                    </div>
                                </div>
                                <Badge variant="outline" className="border-border/80">
                                    {SEARCH_RESULT_LIMIT} / section
                                </Badge>
                            </div>

                            <CommandList className="max-h-[26rem] p-2">
                                {!trimmedQuery ? (
                                    <div className="grid gap-3 p-2 sm:grid-cols-3">
                                        <div className="rounded-xl border border-border/70 bg-background/40 px-3 py-3">
                                            <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                                                <ServerIcon className="size-4 text-primary" />
                                                Instances
                                            </div>
                                            <p className="mt-1 text-xs text-muted-foreground">
                                                Jump directly to server detail pages and runtime state.
                                            </p>
                                        </div>
                                        <div className="rounded-xl border border-border/70 bg-background/40 px-3 py-3">
                                            <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                                                <Layers3Icon className="size-4 text-primary" />
                                                Groups
                                            </div>
                                            <p className="mt-1 text-xs text-muted-foreground">
                                                Open group configs, scaling settings, and maintenance state.
                                            </p>
                                        </div>
                                        <div className="rounded-xl border border-border/70 bg-background/40 px-3 py-3">
                                            <div className="flex items-center gap-2 text-sm font-medium text-foreground">
                                                <UsersIcon className="size-4 text-primary" />
                                                Players
                                            </div>
                                            <p className="mt-1 text-xs text-muted-foreground">
                                                Launch player management directly from the header.
                                            </p>
                                        </div>
                                    </div>
                                ) : errorMessage ? (
                                    <div className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-6 text-sm text-destructive">
                                        {errorMessage}
                                    </div>
                                ) : isSearching ? (
                                    <div className="space-y-2 p-2">
                                        {Array.from({ length: 4 }).map((_, index) => (
                                            <div
                                                key={`search-loading-${index}`}
                                                className="h-14 rounded-xl border border-border/70 bg-muted/40"
                                            />
                                        ))}
                                    </div>
                                ) : totalResultCount === 0 ? (
                                    <div className="rounded-xl border border-dashed border-border/80 bg-background/35 px-4 py-6 text-sm text-muted-foreground">
                                        No matching servers, groups, or players were found for "
                                        {trimmedQuery}".
                                    </div>
                                ) : (
                                    <>
                                        {shouldShowServerSection ? (
                                            <CommandGroup heading="Instances">
                                                {results.servers.map((server) => (
                                                    <CommandItem
                                                        key={server.id}
                                                        value={`server-${server.id}`}
                                                        onSelect={() =>
                                                            handleNavigate(`/servers/${server.id}`)
                                                        }
                                                        className="rounded-xl px-3 py-3"
                                                    >
                                                        <div className="flex size-8 items-center justify-center rounded-lg border border-primary/20 bg-primary/10 text-primary">
                                                            <ServerIcon className="size-4" />
                                                        </div>
                                                        <div className="min-w-0 flex-1">
                                                            <div className="truncate font-medium text-foreground">
                                                                {server.displayName ||
                                                                    server.podName ||
                                                                    server.id}
                                                            </div>
                                                            <div className="truncate text-xs text-muted-foreground">
                                                                {server.podName} / {server.group} /{" "}
                                                                {server.type}
                                                            </div>
                                                        </div>
                                                        <CommandShortcut>
                                                            {server.state}
                                                        </CommandShortcut>
                                                    </CommandItem>
                                                ))}
                                            </CommandGroup>
                                        ) : null}

                                        {shouldShowServerSection &&
                                        (shouldShowGroupSection || shouldShowPlayerSection) ? (
                                            <CommandSeparator />
                                        ) : null}

                                        {shouldShowGroupSection ? (
                                            <CommandGroup heading="Groups">
                                                {results.groups.map((group) => (
                                                    <CommandItem
                                                        key={group.id}
                                                        value={`group-${group.id}`}
                                                        onSelect={() =>
                                                            handleNavigate(
                                                                `/groups/${encodeURIComponent(group.id)}`,
                                                            )
                                                        }
                                                        className="rounded-xl px-3 py-3"
                                                    >
                                                        <div className="flex size-8 items-center justify-center rounded-lg border border-primary/20 bg-primary/10 text-primary">
                                                            <Layers3Icon className="size-4" />
                                                        </div>
                                                        <div className="min-w-0 flex-1">
                                                            <div className="truncate font-medium text-foreground">
                                                                {group.id}
                                                            </div>
                                                            <div className="truncate text-xs text-muted-foreground">
                                                                {group.type}
                                                            </div>
                                                        </div>
                                                        <Badge
                                                            variant="outline"
                                                            className={getGroupStatusBadgeClassName(
                                                                group.maintenance,
                                                            )}
                                                        >
                                                            {group.maintenance
                                                                ? "Maintenance"
                                                                : "Active"}
                                                        </Badge>
                                                    </CommandItem>
                                                ))}
                                            </CommandGroup>
                                        ) : null}

                                        {shouldShowGroupSection && shouldShowPlayerSection ? (
                                            <CommandSeparator />
                                        ) : null}

                                        {shouldShowPlayerSection ? (
                                            <CommandGroup heading="Players">
                                                {results.players.map((player) => (
                                                    <CommandItem
                                                        key={player.uuid}
                                                        value={`player-${player.uuid}`}
                                                        disabled={loadingPlayerUuid === player.uuid}
                                                        onSelect={() => {
                                                            void handleOpenPlayer(player.uuid);
                                                        }}
                                                        className="rounded-xl px-3 py-3"
                                                    >
                                                        <div className="flex size-8 items-center justify-center rounded-lg border border-primary/20 bg-primary/10 text-primary">
                                                            {loadingPlayerUuid === player.uuid ? (
                                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                                            ) : (
                                                                <UsersIcon className="size-4" />
                                                            )}
                                                        </div>
                                                        <div className="min-w-0 flex-1">
                                                            <div className="truncate font-medium text-foreground">
                                                                {player.name}
                                                            </div>
                                                            <div className="truncate text-xs text-muted-foreground">
                                                                {player.permissionGroup} /{" "}
                                                                {player.online
                                                                    ? `Connected ${formatDateTime(player.connectedAt)}`
                                                                    : `First join ${formatDateTime(player.firstJoin)}`}
                                                            </div>
                                                        </div>
                                                        <div className="flex items-center gap-2 text-xs text-muted-foreground">
                                                            <span>Manage</span>
                                                            <ArrowUpRightIcon className="size-3.5" />
                                                        </div>
                                                    </CommandItem>
                                                ))}
                                            </CommandGroup>
                                        ) : null}
                                    </>
                                )}
                            </CommandList>
                        </Card>
                    ) : null}
                </Command>
            </div>

            <PlayerManagementModal
                player={selectedPlayer}
                onClose={() => {
                    setSelectedPlayer(null);
                    closeSearch();
                }}
            />
        </>
    );
};

export default HeaderSearchCommand;
