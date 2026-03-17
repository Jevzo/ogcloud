import { useState } from "react";
import {
    ArrowUpRightIcon,
    Clock3Icon,
    MoreHorizontalIcon,
    SkullIcon,
    TerminalIcon,
    UploadIcon,
} from "lucide-react";

import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogMedia,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { ServerActionKind, ServerRecord } from "@/types/server";

interface ServerActionsMenuProps {
    activeActionKey: string | null;
    align?: "center" | "end" | "start";
    canExecuteCommands?: boolean;
    onAction: (serverId: string, action: ServerActionKind) => Promise<void>;
    onExecuteCommand?: (server: ServerRecord) => void;
    onOpenServer?: (server: ServerRecord) => void;
    server: ServerRecord;
}

const getActionIcon = (action: ServerActionKind) => {
    if (action === "drain") {
        return Clock3Icon;
    }

    if (action === "push") {
        return UploadIcon;
    }

    return SkullIcon;
};

const getActionLabel = (action: ServerActionKind) => {
    if (action === "drain") {
        return "Drain gracefully";
    }

    if (action === "push") {
        return "Force template push";
    }

    return "Kill instance";
};

const isTemplatePushEnabled = (server: ServerRecord) =>
    server.type !== "PROXY" && server.state.toUpperCase() === "RUNNING";

const ServerActionsMenu = ({
    activeActionKey,
    align = "end",
    canExecuteCommands = false,
    onAction,
    onExecuteCommand,
    onOpenServer,
    server,
}: ServerActionsMenuProps) => {
    const [confirmAction, setConfirmAction] = useState<ServerActionKind | null>(null);
    const isActionInProgress = activeActionKey !== null;

    const availableActions: ServerActionKind[] =
        server.type === "PROXY" ? ["drain", "kill"] : ["drain", "push", "kill"];

    return (
        <>
            <DropdownMenu>
                <DropdownMenuTrigger asChild>
                    <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Open actions for ${server.displayName}`}
                        disabled={isActionInProgress}
                    >
                        <MoreHorizontalIcon />
                    </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align={align} className="w-56 min-w-56">
                    <DropdownMenuLabel>Runtime actions</DropdownMenuLabel>
                    {onOpenServer ? (
                        <DropdownMenuItem onSelect={() => onOpenServer(server)}>
                            <ArrowUpRightIcon />
                            Open details
                        </DropdownMenuItem>
                    ) : null}
                    {canExecuteCommands && onExecuteCommand ? (
                        <DropdownMenuItem onSelect={() => onExecuteCommand(server)}>
                            <TerminalIcon />
                            Execute command
                        </DropdownMenuItem>
                    ) : null}
                    {onOpenServer || (canExecuteCommands && onExecuteCommand) ? (
                        <DropdownMenuSeparator />
                    ) : null}
                    {availableActions.map((action) => {
                        const Icon = getActionIcon(action);
                        const actionKey = `${server.id}:${action}`;
                        const isBusy = activeActionKey === actionKey;
                        const isDisabled =
                            isActionInProgress ||
                            (action === "push" && !isTemplatePushEnabled(server));

                        return (
                            <DropdownMenuItem
                                key={action}
                                disabled={isDisabled}
                                variant={action === "kill" ? "destructive" : "default"}
                                onSelect={() => {
                                    if (action === "kill") {
                                        setConfirmAction(action);
                                        return;
                                    }

                                    void onAction(server.id, action);
                                }}
                            >
                                <Icon />
                                {isBusy ? `${getActionLabel(action)}...` : getActionLabel(action)}
                            </DropdownMenuItem>
                        );
                    })}
                </DropdownMenuContent>
            </DropdownMenu>

            <AlertDialog
                open={confirmAction === "kill"}
                onOpenChange={(isOpen) => {
                    if (!isOpen) {
                        setConfirmAction(null);
                    }
                }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-destructive/10 text-destructive">
                            <SkullIcon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>Kill {server.displayName}?</AlertDialogTitle>
                        <AlertDialogDescription>
                            This requests an immediate hard stop for <strong>{server.id}</strong>.
                            Use it only when graceful drain is not appropriate.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isActionInProgress}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            variant="destructive"
                            disabled={isActionInProgress}
                            onClick={() => {
                                void onAction(server.id, "kill");
                                setConfirmAction(null);
                            }}
                        >
                            Kill instance
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
};

export default ServerActionsMenu;
