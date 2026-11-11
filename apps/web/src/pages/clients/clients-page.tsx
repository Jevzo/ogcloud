import type {
  OAuthClientCredentials,
  OAuthClientView,
} from "@ogcloud/api-client";
import {
  getOauthClientListQueryKey,
  useOauthClientCreate,
  useOauthClientList,
} from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  columnsFor,
  DataTable,
  EmptyState,
  Field,
  Input,
  PageHeader,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "@tanstack/react-router";
import { AppWindow, ArrowUpRight, Bot, Plus } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ActionDialog } from "../../action-dialog.tsx";
import { agoColumn, monoColumn } from "../../columns.tsx";
import { loadFailure, useFailureToast } from "../../errors.ts";
import { useIsOwner } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { PermissionPicker } from "../roles/permission-picker.tsx";
import { SecretDialog } from "./secret-dialog.tsx";

const columns = columnsFor<OAuthClientView>();

/** Machine clients for scripts, bots and CI (D-0040), next to OgCloud's own apps. */
export function ClientsPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const isOwner = useIsOwner();
  const clients = useOauthClientList();
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<OAuthClientCredentials | null>(null);

  const clientColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("name", {
          header: () => t("clients.columns.name"),
          cell: (cell) => (
            <Link
              to="/clients/$clientId"
              params={{ clientId: cell.row.original.clientId }}
              className="font-label hover:underline"
            >
              {cell.getValue()}
            </Link>
          ),
        }),
        monoColumn(
          columns,
          "clientId",
          t("clients.columns.clientId"),
          (client) => client.clientId,
        ),
        columns.accessor("firstParty", {
          header: () => t("clients.columns.kind"),
          cell: (cell) =>
            cell.getValue() ? (
              <Badge tone="neutral" icon={AppWindow}>
                {t("clients.firstParty")}
              </Badge>
            ) : (
              <Badge tone="accent" icon={Bot}>
                {t("clients.machine")}
              </Badge>
            ),
        }),
        columns.accessor((client) => client.scopes.length, {
          id: "scopes",
          header: () => t("clients.columns.permissions"),
          cell: (cell) =>
            cell.row.original.firstParty
              ? t("clients.fromUser")
              : t("access.count", { count: cell.getValue() }),
        }),
        agoColumn(
          columns,
          "createdAt",
          t("clients.columns.created"),
          (client) => client.createdAt,
        ),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.clients")}
        description={t("clients.description")}
        actions={
          isOwner ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => setCreating(true)}
            >
              {t("clients.create.title")}
            </Button>
          ) : undefined
        }
      />
      <section className="rounded-card border border-border-subtle bg-surface py-1 shadow-card">
        <DataTable
          label={t("nav.clients")}
          data={clients.data?.data ?? []}
          failure={loadFailure(clients)}
          columns={clientColumns}
          getRowId={(client) => client.clientId}
          actions={(client) => ({
            menu: [
              [
                {
                  label: t("clients.open"),
                  icon: ArrowUpRight,
                  onSelect: () =>
                    navigate({
                      to: "/clients/$clientId",
                      params: { clientId: client.clientId },
                    }),
                },
              ],
            ],
          })}
          loading={clients.isPending}
          empty={
            <EmptyState
              icon={Bot}
              title={t("clients.empty")}
              description={t("clients.emptyDescription")}
            />
          }
        />
      </section>
      <CreateClientDialog
        open={creating}
        onOpenChange={setCreating}
        onCreated={(credentials) => {
          setCreating(false);
          setCreated(credentials);
        }}
      />
      <SecretDialog
        credentials={created}
        onClose={() => {
          const clientId = created?.clientId;
          setCreated(null);
          if (clientId !== undefined) {
            void navigate({ to: "/clients/$clientId", params: { clientId } });
          }
        }}
      />
    </div>
  );
}

/** A machine client needs a name and its permissions; the secret comes back once. */
function CreateClientDialog({
  open,
  onOpenChange,
  onCreated,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: (credentials: OAuthClientCredentials) => void;
}) {
  const { t } = useTranslation("web");
  const failed = useFailureToast();
  const client = useQueryClient();
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<string[]>([]);
  const create = useOauthClientCreate({
    mutation: {
      onSettled: () =>
        invalidatePaths(client, [getOauthClientListQueryKey()[0]]),
    },
  });

  return (
    <ActionDialog
      open={open}
      onOpenChange={onOpenChange}
      className="w-[min(44rem,calc(100vw-2rem))]"
      title={t("clients.create.title")}
      description={t("clients.create.description")}
      submitLabel={t("clients.create.submit")}
      onSubmit={() =>
        create.mutate(
          { data: { name: name.trim(), scopes } },
          {
            onSuccess: (answer) => {
              setName("");
              setScopes([]);
              onCreated(answer.data);
            },
            onError: failed,
          },
        )
      }
      disabled={name.trim() === "" || scopes.length === 0}
      busy={create.isPending}
    >
      <Field label={t("clients.form.name")} hint={t("clients.form.nameHint")}>
        {(control) => (
          <Input
            {...control}
            value={name}
            placeholder={t("clients.form.namePlaceholder")}
            onChange={(event) => setName(event.target.value)}
          />
        )}
      </Field>
      <div className="flex flex-col gap-2">
        <span className="font-label">{t("access.title")}</span>
        <PermissionPicker value={scopes} onChange={setScopes} />
      </div>
    </ActionDialog>
  );
}
