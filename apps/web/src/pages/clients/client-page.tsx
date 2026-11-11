import type {
  OAuthClientCredentials,
  OAuthClientRequest,
  OAuthClientView,
} from "@ogcloud/api-client";
import {
  getOauthClientListQueryKey,
  useOauthClientDelete,
  useOauthClientList,
  useOauthClientRotateSecret,
  useOauthClientUpdate,
} from "@ogcloud/api-client/react";
import { OauthClientUpdateBody } from "@ogcloud/api-client/zod";
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  CopyField,
  Note,
  PageHeader,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "@tanstack/react-router";
import { AppWindow, Bot, KeyRound, Lock } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { Ago } from "../../ago.tsx";
import { useDeleteAction } from "../../delete-action.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { DetailRow, Details } from "../../details.tsx";
import { EditorForm, useListSave } from "../../editing.tsx";
import { useFailureToast } from "../../errors.ts";
import { contractSchema, useSettingsForm } from "../../form.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { useIsOwner } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { PermissionsCard } from "../roles/permission-picker.tsx";
import { SecretDialog } from "./secret-dialog.tsx";

const SettingsForm = contractSchema<{ settings: OAuthClientRequest }>(
  z.object({ settings: OauthClientUpdateBody }),
);

/** One OAuth client: a machine client's name and permissions, edited by owners, or one of OgCloud's own apps. */
export function ClientPage() {
  const { t } = useTranslation("web");
  const { clientId } = useParams({ from: "/app/clients/$clientId" });
  const clients = useOauthClientList();
  const client = clients.data?.data.find(
    (candidate) => candidate.clientId === clientId,
  );

  if (client === undefined) {
    return (
      <DetailFallback
        error={clients.error}
        missing={clients.isSuccess}
        icon={Bot}
        goneTitle={t("clients.detail.gone")}
        goneDescription={t("clients.detail.goneDescription")}
        failedTitle={t("clients.detail.failed")}
        back={
          <Link to="/clients" className="font-label text-accent-text">
            {t("clients.detail.back")}
          </Link>
        }
      />
    );
  }
  // Clients carry no version; what they hold now keys the form, so a save or a reload starts it afresh.
  return <ClientEditor key={client.clientId} client={client} />;
}

function ClientEditor({ client }: { client: OAuthClientView }) {
  const { t } = useTranslation("web");
  const failed = useFailureToast();
  const navigate = useNavigate();
  const queries = useQueryClient();
  const isOwner = useIsOwner();
  const canWrite = isOwner && !client.firstParty;
  const [rotating, setRotating] = useState(false);
  const [rotated, setRotated] = useState<OAuthClientCredentials | null>(null);
  const list = getOauthClientListQueryKey()[0];
  const update = useOauthClientUpdate();
  const rotate = useOauthClientRotateSecret();
  const remove = useOauthClientDelete({
    mutation: { onSettled: () => invalidatePaths(queries, [list]) },
  });
  const deletion = useDeleteAction({
    label: t("clients.detail.delete"),
    title: t("clients.detail.deleteTitle", { name: client.name }),
    description: t("clients.detail.deleteDescription"),
    typedName: client.clientId,
    done: t("clients.detail.deleted", { name: client.name }),
    remove: () => remove.mutateAsync({ clientId: client.clientId }),
    onDeleted: () => void navigate({ to: "/clients" }),
  });
  const outcome = useListSave(list);

  const { form, changes, saving } = useSettingsForm({
    view: client,
    settingsOf: ({ name, scopes }) => ({ name, scopes }),
    schema: SettingsForm,
    save: (settings, base) =>
      update.mutateAsync({ clientId: base.clientId, data: settings }),
    ...outcome,
  });

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {client.name}
            {client.firstParty ? (
              <Badge tone="neutral" icon={AppWindow}>
                {t("clients.firstParty")}
              </Badge>
            ) : null}
          </span>
        }
        description={t("clients.detail.description")}
        actions={
          canWrite ? (
            <>
              <MoreMenu groups={[[deletion.item]]} />
              <Button icon={KeyRound} onClick={() => setRotating(true)}>
                {t("clients.detail.rotate")}
              </Button>
            </>
          ) : undefined
        }
      />
      <EditorForm
        form={form}
        canWrite={canWrite}
        changes={changes}
        saving={saving}
        aside={
          <Card title={t("clients.detail.identity")}>
            <div className="flex flex-col gap-3">
              <CopyField
                value={client.clientId}
                label={t("clients.columns.clientId")}
              />
              <Details>
                <DetailRow label={t("clients.columns.created")}>
                  <Ago at={client.createdAt} />
                </DetailRow>
              </Details>
            </div>
          </Card>
        }
      >
        {client.firstParty ? (
          <Note tone="info" icon={Lock}>
            {t("clients.detail.firstPartyNote")}
          </Note>
        ) : isOwner ? null : (
          <Note tone="info" icon={Lock}>
            {t("clients.detail.ownerOnly")}
          </Note>
        )}
        <Card title={t("clients.form.basics")}>
          <form.AppField name="settings.name">
            {(field) => <field.TextField label={t("clients.form.name")} />}
          </form.AppField>
        </Card>
        {client.firstParty ? null : (
          <form.Field name="settings.scopes">
            {(field) => (
              <PermissionsCard
                value={field.state.value}
                onChange={field.handleChange}
                disabled={!canWrite}
              />
            )}
          </form.Field>
        )}
      </EditorForm>
      <ConfirmDialog
        open={rotating}
        onOpenChange={setRotating}
        title={t("clients.detail.rotateTitle", { name: client.name })}
        description={t("clients.detail.rotateDescription")}
        confirmLabel={t("clients.detail.rotate")}
        // The old secret stops working at once, which cannot be undone (D-0210).
        typedName={client.clientId}
        onConfirm={() =>
          rotate
            .mutateAsync({ clientId: client.clientId })
            .then((answer) => setRotated(answer.data), failed)
        }
      />
      {deletion.dialog}
      <SecretDialog credentials={rotated} onClose={() => setRotated(null)} />
    </div>
  );
}
