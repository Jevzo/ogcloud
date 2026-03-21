import { useState } from "react";
import {
    ArrowUpRightIcon,
    LoaderCircleIcon,
    MoreHorizontalIcon,
    RotateCcwIcon,
    ShieldAlertIcon,
    ShieldCheckIcon,
} from "lucide-react";
import { toast } from "sonner";

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
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { GroupRecord } from "@/types/group";

interface GroupActionsMenuProps {
    activeActionKey: string | null;
    align?: "center" | "end" | "start";
    group: GroupRecord;
    onOpenGroup?: (group: GroupRecord) => void;
    onRestartGroup: (groupId: string) => Promise<void>;
    onToggleMaintenance: (groupId: string, maintenance: boolean) => Promise<void>;
}

const createRestartConfirmationCode = () => `${Math.floor(100000 + Math.random() * 900000)}`;

const GroupActionsMenu = ({
    activeActionKey,
    align = "end",
    group,
    onOpenGroup,
    onRestartGroup,
    onToggleMaintenance,
}: GroupActionsMenuProps) => {
    const [isMaintenanceAlertOpen, setIsMaintenanceAlertOpen] = useState(false);
    const [isRestartDialogOpen, setIsRestartDialogOpen] = useState(false);
    const [restartConfirmationCode, setRestartConfirmationCode] = useState("");
    const [restartConfirmationInput, setRestartConfirmationInput] = useState("");
    const [restartErrorMessage, setRestartErrorMessage] = useState<string | null>(null);

    const maintenanceActionKey = `${group.id}:maintenance:${group.maintenance ? "off" : "on"}`;
    const restartActionKey = `${group.id}:restart`;
    const isActionInProgress = activeActionKey !== null;
    const isUpdatingMaintenance = activeActionKey === maintenanceActionKey;
    const isRestarting = activeActionKey === restartActionKey;

    const openRestartDialog = () => {
        if (!group.maintenance) {
            toast.error("Enable group maintenance before requesting a restart.");
            return;
        }

        setRestartConfirmationCode(createRestartConfirmationCode());
        setRestartConfirmationInput("");
        setRestartErrorMessage(null);
        setIsRestartDialogOpen(true);
    };

    const handleEnableMaintenance = async () => {
        try {
            await onToggleMaintenance(group.id, true);
            setIsMaintenanceAlertOpen(false);
        } catch {
            return;
        }
    };

    const handleRestartGroup = async () => {
        if (restartConfirmationInput.trim() !== restartConfirmationCode) {
            setRestartErrorMessage("Enter the generated 6-digit code to confirm the restart.");
            return;
        }

        setRestartErrorMessage(null);

        try {
            await onRestartGroup(group.id);
            setIsRestartDialogOpen(false);
        } catch {
            return;
        }
    };

    return (
        <>
            <DropdownMenu>
                <DropdownMenuTrigger asChild>
                    <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`Open actions for ${group.id}`}
                        disabled={isActionInProgress}
                    >
                        <MoreHorizontalIcon />
                    </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align={align} className="w-56 min-w-56">
                    <DropdownMenuLabel>Group actions</DropdownMenuLabel>
                    {onOpenGroup ? (
                        <>
                            <DropdownMenuItem onSelect={() => onOpenGroup(group)}>
                                <ArrowUpRightIcon />
                                Open details
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                        </>
                    ) : null}
                    <DropdownMenuItem
                        disabled={isActionInProgress}
                        onSelect={() => {
                            if (group.maintenance) {
                                void onToggleMaintenance(group.id, false);
                                return;
                            }

                            setIsMaintenanceAlertOpen(true);
                        }}
                    >
                        {group.maintenance ? <ShieldCheckIcon /> : <ShieldAlertIcon />}
                        {isUpdatingMaintenance
                            ? "Updating maintenance..."
                            : group.maintenance
                              ? "Disable maintenance"
                              : "Enable maintenance"}
                    </DropdownMenuItem>
                    <DropdownMenuItem
                        disabled={isActionInProgress}
                        onSelect={() => openRestartDialog()}
                    >
                        <RotateCcwIcon />
                        {isRestarting ? "Restarting group..." : "Restart group"}
                    </DropdownMenuItem>
                </DropdownMenuContent>
            </DropdownMenu>

            <AlertDialog open={isMaintenanceAlertOpen} onOpenChange={setIsMaintenanceAlertOpen}>
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogMedia className="bg-amber-500/10 text-amber-300">
                            <ShieldAlertIcon />
                        </AlertDialogMedia>
                        <AlertDialogTitle>Enable maintenance for {group.id}?</AlertDialogTitle>
                        <AlertDialogDescription>
                            Enabling maintenance can disconnect active players from this group and
                            block new joins until it is disabled again.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isUpdatingMaintenance}>
                            Cancel
                        </AlertDialogCancel>
                        <AlertDialogAction
                            variant="outline"
                            disabled={isUpdatingMaintenance}
                            onClick={() => void handleEnableMaintenance()}
                        >
                            {isUpdatingMaintenance ? "Enabling..." : "Enable maintenance"}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>

            <Dialog
                open={isRestartDialogOpen}
                onOpenChange={(nextOpen) => {
                    if (isRestarting) {
                        return;
                    }

                    setIsRestartDialogOpen(nextOpen);
                }}
            >
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <RotateCcwIcon className="size-4 text-destructive" />
                            Restart group
                        </DialogTitle>
                        <DialogDescription>
                            This requests a phased restart for {group.id}. Type the generated code
                            to confirm the action.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4">
                        <div className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive/85">
                            The backend will restart active servers in this group. Use this only
                            after you have confirmed maintenance mode is enabled.
                        </div>

                        <div className="rounded-xl border border-border/70 bg-muted/35 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Confirmation code
                            </div>
                            <div className="mt-2 font-mono text-2xl font-semibold tracking-[0.35em] text-foreground">
                                {restartConfirmationCode}
                            </div>
                        </div>

                        <div className="space-y-2">
                            <FieldLabel htmlFor={`restart-confirmation-${group.id}`}>
                                Type the 6-digit code
                            </FieldLabel>
                            <Input
                                id={`restart-confirmation-${group.id}`}
                                value={restartConfirmationInput}
                                onChange={(event) =>
                                    setRestartConfirmationInput(
                                        event.target.value.replace(/\D/g, "").slice(0, 6),
                                    )
                                }
                                className="font-mono tracking-[0.28em]"
                                inputMode="numeric"
                                maxLength={6}
                                autoFocus
                            />
                            <FieldError>{restartErrorMessage}</FieldError>
                        </div>
                    </div>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setIsRestartDialogOpen(false)}
                            disabled={isRestarting}
                        >
                            Cancel
                        </Button>
                        <Button
                            type="button"
                            variant="destructive"
                            onClick={() => void handleRestartGroup()}
                            disabled={isRestarting || restartConfirmationInput.trim().length !== 6}
                        >
                            {isRestarting ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Requesting
                                </>
                            ) : (
                                "Request restart"
                            )}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
};

export default GroupActionsMenu;
