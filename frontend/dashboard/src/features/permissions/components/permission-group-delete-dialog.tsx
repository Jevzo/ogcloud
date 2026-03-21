import { useState } from "react";
import { Trash2Icon } from "lucide-react";
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
import { deletePermissionGroup } from "@/api";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { useNetworkSettingsStore } from "@/store/network-settings-store";

interface PermissionGroupDeleteDialogProps {
    groupId?: string;
    groupName: string;
    onDeleted?: () => Promise<void> | void;
    onOpenChange: (open: boolean) => void;
    open: boolean;
}

const PermissionGroupDeleteDialog = ({
    groupId,
    groupName,
    onDeleted,
    onOpenChange,
    open,
}: PermissionGroupDeleteDialogProps) => {
    const getAccessToken = useAccessToken();
    const permissionSystemEnabled = useNetworkSettingsStore(
        (state) => state.general.permissionSystemEnabled,
    );
    const [isDeletingGroup, setIsDeletingGroup] = useState(false);

    const handleDeleteGroup = async () => {
        if (!groupId) {
            return;
        }

        if (!permissionSystemEnabled) {
            toast.error("Permission system is disabled in network settings.");
            return;
        }

        setIsDeletingGroup(true);

        try {
            const accessToken = await getAccessToken();

            await deletePermissionGroup(accessToken, groupId);
            toast.success(`Deleted permission group ${groupName}.`);
            await onDeleted?.();
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to delete permission group.",
            );
        } finally {
            setIsDeletingGroup(false);
            onOpenChange(false);
        }
    };

    return (
        <AlertDialog open={open} onOpenChange={onOpenChange}>
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogMedia className="bg-destructive/10 text-destructive">
                        <Trash2Icon />
                    </AlertDialogMedia>
                    <AlertDialogTitle>Delete {groupName}?</AlertDialogTitle>
                    <AlertDialogDescription>
                        Players currently assigned to this group will fall back to the default
                        permission group after deletion.
                    </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={isDeletingGroup}>Cancel</AlertDialogCancel>
                    <AlertDialogAction
                        variant="destructive"
                        disabled={isDeletingGroup || !groupId}
                        onClick={() => void handleDeleteGroup()}
                    >
                        {isDeletingGroup ? "Deleting..." : "Delete group"}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    );
};

export default PermissionGroupDeleteDialog;
