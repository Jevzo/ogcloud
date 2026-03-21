import { ArrowUpRightIcon, MoreHorizontalIcon, Trash2Icon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { PermissionGroupRecord } from "@/types/permission";

interface PermissionGroupActionsMenuProps {
    align?: "center" | "end" | "start";
    group: PermissionGroupRecord;
    onDeleteGroup: (group: PermissionGroupRecord) => void;
    onOpenGroup: (group: PermissionGroupRecord) => void;
}

const PermissionGroupActionsMenu = ({
    align = "end",
    group,
    onDeleteGroup,
    onOpenGroup,
}: PermissionGroupActionsMenuProps) => (
    <DropdownMenu>
        <DropdownMenuTrigger asChild>
            <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={`Open actions for ${group.name}`}
            >
                <MoreHorizontalIcon />
            </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align={align} className="w-56 min-w-56">
            <DropdownMenuLabel>Group actions</DropdownMenuLabel>
            <DropdownMenuItem onSelect={() => onOpenGroup(group)}>
                <ArrowUpRightIcon />
                Open details
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
                className="text-destructive focus:text-destructive"
                onSelect={() => onDeleteGroup(group)}
            >
                <Trash2Icon />
                Delete group
            </DropdownMenuItem>
        </DropdownMenuContent>
    </DropdownMenu>
);

export default PermissionGroupActionsMenu;
