import type { UserView } from "@ogcloud/api-client";
import {
  getUserListQueryKey,
  useUserDelete,
  useUserResetPassword,
  useUserRevokeLogins,
  useUserUnlinkMinecraft,
} from "@ogcloud/api-client/react";
import { ConfirmDialog, type MenuItem, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import {
  KeyRound,
  LogOut,
  type LucideIcon,
  Trash2,
  Unlink,
} from "lucide-react";
import { type ReactNode, useState } from "react";
import { useTranslation } from "react-i18next";
import { ActionDialog, useOnOpen } from "../../action-dialog.tsx";
import { useFailureToast } from "../../errors.ts";
import { usePendingAction } from "../../pending-action.ts";
import { isOwner, useCan, useIsOwner } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { isValidPassword, NewPasswordField } from "./new-password-field.tsx";

export type UserAction = "reset" | "signOut" | "unlink" | "delete";

/** Menu groups without the actions the caller may not take, and without groups left empty. */
export function groupsOf(
  groups: readonly (readonly (MenuItem | undefined)[])[],
): MenuItem[][] {
  return groups
    .map((group) =>
      group.filter((item): item is MenuItem => item !== undefined),
    )
    .filter((group) => group.length > 0);
}

/**
 * A dashboard user's account actions with their confirmations. Holders of `users:write` manage everyone but owners,
 * whom only owners manage (D-0094); each of these ends the user's logins except unlinking.
 */
export function useUserActions(onDeleted: () => void = () => undefined): {
  canManage: (user: UserView) => boolean;
  open: (action: UserAction, user: UserView) => void;
  /** The actions the caller may take on this user, by name; none for a user they may not manage. */
  itemsFor: (user: UserView) => Partial<Record<UserAction, MenuItem>>;
  dialogs: ReactNode;
} {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const queries = useQueryClient();
  const canWrite = useCan("users:write");
  const callerIsOwner = useIsOwner();
  const pending = usePendingAction<UserAction, UserView>();
  const refresh = {
    onSettled: () => invalidatePaths(queries, [getUserListQueryKey()[0]]),
  };
  const reset = useUserResetPassword();
  const signOut = useUserRevokeLogins();
  const unlink = useUserUnlinkMinecraft({ mutation: refresh });
  const remove = useUserDelete({ mutation: refresh });

  const canManage = (user: UserView) =>
    canWrite && (callerIsOwner || !isOwner(user));
  const done = (message: string) => () => toast({ message });
  const user = pending.target;
  const name = user?.username ?? "";

  return {
    canManage,
    open: pending.open,
    itemsFor: (target) => {
      if (!canManage(target)) return {};
      const item = (action: UserAction, icon: LucideIcon, danger = false) => ({
        label: t(`users.actions.${action}`),
        icon,
        danger,
        onSelect: () => pending.open(action, target),
      });
      return {
        reset: item("reset", KeyRound),
        signOut: item("signOut", LogOut),
        ...(target.linkedPlayerUuid == null
          ? {}
          : { unlink: item("unlink", Unlink) }),
        delete: item("delete", Trash2, true),
      };
    },
    dialogs: (
      <>
        <ResetPasswordDialog
          open={pending.isOpen("reset")}
          onOpenChange={pending.onOpenChange}
          name={name}
          busy={reset.isPending}
          onSubmit={(password) =>
            user === undefined
              ? undefined
              : reset.mutate(
                  { id: user.id, data: { password } },
                  {
                    onSuccess: () => {
                      pending.onOpenChange(false);
                      toast({
                        message: t("users.actions.resetDone", { name }),
                      });
                    },
                    onError: failed,
                  },
                )
          }
        />
        <ConfirmDialog
          open={pending.isOpen("signOut")}
          onOpenChange={pending.onOpenChange}
          title={t("users.actions.signOutTitle", { name })}
          description={t("users.actions.signOutDescription")}
          confirmLabel={t("users.actions.signOut")}
          onConfirm={() =>
            user === undefined
              ? undefined
              : signOut
                  .mutateAsync({ id: user.id })
                  .then(done(t("users.actions.signOutDone", { name })), failed)
          }
        />
        <ConfirmDialog
          open={pending.isOpen("unlink")}
          onOpenChange={pending.onOpenChange}
          title={t("users.actions.unlinkTitle", { name })}
          description={t("users.actions.unlinkDescription", {
            player: user?.linkedPlayerName ?? "",
          })}
          confirmLabel={t("users.actions.unlink")}
          onConfirm={() =>
            user === undefined
              ? undefined
              : unlink
                  .mutateAsync({ id: user.id })
                  .then(done(t("users.actions.unlinkDone", { name })), failed)
          }
        />
        <ConfirmDialog
          open={pending.isOpen("delete")}
          onOpenChange={pending.onOpenChange}
          title={t("users.actions.deleteTitle", { name })}
          description={t("users.actions.deleteDescription")}
          confirmLabel={t("users.actions.delete")}
          typedName={name}
          onConfirm={() =>
            user === undefined
              ? undefined
              : remove.mutateAsync({ id: user.id }).then(() => {
                  toast({ message: t("users.actions.deleted", { name }) });
                  onDeleted();
                }, failed)
          }
        />
      </>
    ),
  };
}

function ResetPasswordDialog({
  open,
  onOpenChange,
  name,
  busy,
  onSubmit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  name: string;
  busy: boolean;
  onSubmit: (password: string) => void;
}) {
  const { t } = useTranslation("web");
  const [password, setPassword] = useState("");
  useOnOpen(open, () => {
    setPassword("");
  });
  return (
    <ActionDialog
      open={open}
      onOpenChange={(next) => {
        if (!next) setPassword("");
        onOpenChange(next);
      }}
      title={t("users.actions.resetTitle", { name })}
      description={t("users.actions.resetDescription")}
      submitLabel={t("users.actions.reset")}
      onSubmit={() => onSubmit(password)}
      disabled={!isValidPassword(password)}
      busy={busy}
    >
      <NewPasswordField value={password} onChange={setPassword} />
    </ActionDialog>
  );
}
