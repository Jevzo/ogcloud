import { ArrowUpRightIcon, LogOutIcon, MoreHorizontalIcon, ShieldAlertIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuShortcut,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import type { PersistedPlayerRecord } from "@/types/player";

interface PlayerActionsMenuProps {
    align?: "center" | "end" | "start";
    onOpenPlayer: (player: PersistedPlayerRecord) => void;
    player: PersistedPlayerRecord;
}

const PlayerActionsMenu = ({ align = "end", onOpenPlayer, player }: PlayerActionsMenuProps) => (
    <TooltipProvider delayDuration={150}>
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button
                    type="button"
                    variant="ghost"
                    size="icon-sm"
                    aria-label={`Open actions for ${player.name}`}
                >
                    <MoreHorizontalIcon />
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align={align} className="w-56 min-w-56">
                <DropdownMenuLabel>Player actions</DropdownMenuLabel>
                <DropdownMenuItem onSelect={() => onOpenPlayer(player)}>
                    <ArrowUpRightIcon />
                    Open details
                </DropdownMenuItem>
                <DropdownMenuSeparator />

                <Tooltip>
                    <TooltipTrigger asChild>
                        <DropdownMenuItem
                            className="cursor-help text-muted-foreground focus:text-foreground"
                            onSelect={(event) => event.preventDefault()}
                        >
                            <LogOutIcon />
                            Kick player
                            <DropdownMenuShortcut>Soon</DropdownMenuShortcut>
                        </DropdownMenuItem>
                    </TooltipTrigger>
                    <TooltipContent side="left" sideOffset={8}>
                        Kick actions are currently in the making.
                    </TooltipContent>
                </Tooltip>

                <Tooltip>
                    <TooltipTrigger asChild>
                        <DropdownMenuItem
                            className="cursor-help text-muted-foreground focus:text-foreground"
                            onSelect={(event) => event.preventDefault()}
                        >
                            <ShieldAlertIcon />
                            Ban player
                            <DropdownMenuShortcut>Soon</DropdownMenuShortcut>
                        </DropdownMenuItem>
                    </TooltipTrigger>
                    <TooltipContent side="left" sideOffset={8}>
                        Ban actions are currently in the making.
                    </TooltipContent>
                </Tooltip>
            </DropdownMenuContent>
        </DropdownMenu>
    </TooltipProvider>
);

export default PlayerActionsMenu;
