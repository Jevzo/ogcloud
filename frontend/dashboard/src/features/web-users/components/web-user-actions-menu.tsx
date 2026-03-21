import {
    Link2OffIcon,
    LoaderCircleIcon,
    MoreHorizontalIcon,
    PencilLineIcon,
    Trash2Icon,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { WebUserRecord } from "@/types/web-user";

interface WebUserActionsMenuProps {
    align?: "center" | "end" | "start";
    disabled?: boolean;
    isUnlinking?: boolean;
    onDelete: (user: WebUserRecord) => void;
    onEdit: (user: WebUserRecord) => void;
    onUnlink: (user: WebUserRecord) => void;
    user: WebUserRecord;
}

const WebUserActionsMenu = ({
    align = "end",
    disabled = false,
    isUnlinking = false,
    onDelete,
    onEdit,
    onUnlink,
    user,
}: WebUserActionsMenuProps) => (
    <DropdownMenu>
        <DropdownMenuTrigger asChild>
            <Button
                type="button"
                variant="ghost"
                size="icon-sm"
                aria-label={`Open actions for ${user.email}`}
                disabled={disabled}
            >
                {isUnlinking ? (
                    <LoaderCircleIcon className="size-4 animate-spin" />
                ) : (
                    <MoreHorizontalIcon />
                )}
            </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align={align} className="w-56 min-w-56">
            <DropdownMenuLabel>Account actions</DropdownMenuLabel>
            <DropdownMenuItem
                disabled={!user.linkedPlayerUuid || disabled}
                onSelect={(event) => {
                    event.preventDefault();
                    onUnlink(user);
                }}
            >
                <Link2OffIcon />
                Unlink Minecraft account
            </DropdownMenuItem>
            <DropdownMenuItem
                disabled={disabled}
                onSelect={(event) => {
                    event.preventDefault();
                    onEdit(user);
                }}
            >
                <PencilLineIcon />
                Edit user
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
                variant="destructive"
                disabled={disabled}
                onSelect={(event) => {
                    event.preventDefault();
                    onDelete(user);
                }}
            >
                <Trash2Icon />
                Delete user
            </DropdownMenuItem>
        </DropdownMenuContent>
    </DropdownMenu>
);

export default WebUserActionsMenu;
