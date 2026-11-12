import type { UserView } from "@ogcloud/api-client";
import {
  getUserListQueryKey,
  useUserCreate,
  useUserListHook,
} from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  Field,
  Input,
  PageHeader,
  Select,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, Plus, UserRound, Users } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ActionDialog, useOnOpen } from "../../action-dialog.tsx";
import { agoColumn } from "../../columns.tsx";
import { loadFailure, useFailureToast } from "../../errors.ts";
import { LoadMore } from "../../load-more.tsx";
import { PAGE_SIZE, usePaged } from "../../paged.ts";
import { useIsOwner, useMe } from "../../permissions.ts";
import { placeholderUser } from "../../placeholders.ts";
import { invalidatePaths } from "../../queries.ts";
import { isValidPassword, NewPasswordField } from "./new-password-field.tsx";
import { useRoleNames } from "./role-names.ts";
import { groupsOf, useUserActions } from "./user-actions.tsx";
import { UserStatusBadge } from "./user-status.tsx";

const columns = columnsFor<UserView>();

/** Everyone who signs in to the dashboard, with their role and Minecraft link. */
export function UsersPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const me = useMe();
  const isOwner = useIsOwner();
  const roles = useRoleNames();
  const actions = useUserActions();
  const fetchUsers = useUserListHook();
  const users = usePaged(
    getUserListQueryKey(),
    async (cursor) =>
      (
        await fetchUsers({
          limit: PAGE_SIZE,
          ...(cursor === undefined ? {} : { cursor }),
        })
      ).data,
  );
  const [creating, setCreating] = useState(false);

  const userColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("username", {
          header: () => t("users.columns.user"),
          cell: (cell) => (
            <span className="flex items-center gap-2">
              <Link
                to="/users/$userId"
                params={{ userId: cell.row.original.id }}
                className="font-label hover:underline"
              >
                {cell.getValue()}
              </Link>
              {cell.row.original.id === me.id ? (
                <Badge tone="accent" icon={UserRound}>
                  {t("users.you")}
                </Badge>
              ) : null}
            </span>
          ),
        }),
        columns.accessor("email", {
          header: () => t("users.columns.email"),
          cell: (cell) => (
            <span className="text-text-2">{cell.getValue()}</span>
          ),
        }),
        columns.accessor((user) => roles.nameOf(user.roleId), {
          id: "role",
          header: () => t("users.columns.role"),
        }),
        columns.accessor((user) => user.linkedPlayerName ?? "", {
          id: "minecraft",
          header: () => t("users.columns.minecraft"),
          cell: (cell) =>
            cell.getValue() === "" ? (
              <span className="text-text-3">{t("users.notLinked")}</span>
            ) : (
              <span className="font-mono text-mono">{cell.getValue()}</span>
            ),
        }),
        columns.accessor("enabled", {
          header: () => t("users.columns.status"),
          cell: (cell) => <UserStatusBadge enabled={cell.getValue()} />,
        }),
        agoColumn(
          columns,
          "createdAt",
          t("users.columns.created"),
          (user) => user.createdAt,
        ),
      ]),
    [t, me.id, roles],
  );

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.users")}
        actions={
          isOwner ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => setCreating(true)}
            >
              {t("users.create.title")}
            </Button>
          ) : undefined
        }
      />
      <Card flush>
        <DataTable
          label={t("nav.users")}
          data={users.items}
          failure={loadFailure(users)}
          columns={userColumns}
          getRowId={(user) => user.id}
          placeholder={placeholderUser}
          actions={(user) => {
            const items = actions.itemsFor(user);
            return {
              menu: groupsOf([
                [
                  {
                    label: t("users.open"),
                    icon: ArrowUpRight,
                    onSelect: () =>
                      navigate({
                        to: "/users/$userId",
                        params: { userId: user.id },
                      }),
                  },
                ],
                [items.reset, items.signOut, items.unlink],
                [items.delete],
              ]),
            };
          }}
          loading={users.pending}
          empty={
            <EmptyState
              icon={Users}
              title={t("users.empty")}
              description={t("users.emptyDescription")}
            />
          }
        />
        <LoadMore paged={users} />
      </Card>
      <CreateUserDialog
        open={creating}
        onOpenChange={setCreating}
        roles={roles.options}
      />
      {actions.dialogs}
    </div>
  );
}

/** Owners add users with a first password they pass on; the user changes it (D-0094). */
function CreateUserDialog({
  open,
  onOpenChange,
  roles,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  roles: { value: string; label: string }[];
}) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const navigate = useNavigate();
  const queries = useQueryClient();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [roleId, setRoleId] = useState<string | undefined>(undefined);
  const [password, setPassword] = useState("");
  useOnOpen(open, () => {
    setUsername("");
    setEmail("");
    setRoleId(undefined);
    setPassword("");
  });
  const create = useUserCreate({
    mutation: {
      onSettled: () => invalidatePaths(queries, [getUserListQueryKey()[0]]),
    },
  });
  const ready =
    username.trim() !== "" &&
    email.includes("@") &&
    roleId !== undefined &&
    isValidPassword(password);

  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      title={t("users.create.title")}
      description={t("users.create.description")}
      submitLabel={t("users.create.submit")}
      disabled={!ready}
      busy={create.isPending}
      onSubmit={() =>
        roleId === undefined
          ? undefined
          : create.mutate(
              {
                data: {
                  username: username.trim(),
                  email: email.trim(),
                  roleId,
                  password,
                },
              },
              {
                onSuccess: (answer) => {
                  toast({
                    message: t("users.create.done", {
                      name: username.trim(),
                    }),
                  });
                  onOpenChange(false);
                  void navigate({
                    to: "/users/$userId",
                    params: { userId: answer.data.id },
                  });
                },
                onError: failed,
              },
            )
      }
    >
      <Field label={t("users.form.username")}>
        {(control) => (
          <Input
            {...control}
            value={username}
            autoComplete="off"
            onChange={(event) => setUsername(event.target.value)}
          />
        )}
      </Field>
      <Field label={t("users.form.email")}>
        {(control) => (
          <Input
            {...control}
            type="email"
            value={email}
            autoComplete="off"
            onChange={(event) => setEmail(event.target.value)}
          />
        )}
      </Field>
      <Field label={t("users.form.role")}>
        {(control) => (
          <Select
            {...control}
            options={roles}
            value={roleId}
            placeholder={t("users.form.rolePlaceholder")}
            onValueChange={setRoleId}
          />
        )}
      </Field>
      <NewPasswordField value={password} onChange={setPassword} />
    </ActionDialog>
  );
}
