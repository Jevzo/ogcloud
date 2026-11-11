import type {
  NetworkSettingsFields,
  NetworkSettingsView,
} from "@ogcloud/api-client";
import {
  getNetworkGetQueryKey,
  useMeGet,
  useNetworkAcceptEula,
  useNetworkGet,
  useNetworkRestart,
  useNetworkRotateForwardingToken,
  useNetworkUpdate,
  useOverviewGet,
} from "@ogcloud/api-client/react";
import { NetworkUpdateBody } from "@ogcloud/api-client/zod";
import {
  Button,
  Card,
  ConfirmDialog,
  Note,
  PageHeader,
  useToast,
} from "@ogcloud/ui";
import { useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import {
  Construction,
  FileWarning,
  Globe,
  KeyRound,
  Play,
  RotateCw,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { z } from "zod";
import { Ago } from "../../ago.tsx";
import { DetailFallback } from "../../detail-fallback.tsx";
import { EditorForm, useSaved, useSaveFailure } from "../../editing.tsx";
import { useFailureToast } from "../../errors.ts";
import { contractSchema, useSettingsForm } from "../../form.tsx";
import { MoreMenu } from "../../more-menu.tsx";
import { usePendingAction } from "../../pending-action.ts";
import { useCan } from "../../permissions.ts";
import { useTextValidator } from "../../texts.ts";
import { useBackendGroupIds } from "../groups/group-ids.ts";
import { useNetworkMaintenance } from "./network-actions.ts";
import { NetworkLook } from "./network-look.tsx";
import { ROUTING_STRATEGIES } from "./routing.ts";
import { RuntimeCard } from "./runtime-card.tsx";

const OWNER = "owner";
// Maintenance has its own action in the header, so the form leaves it out.
const SettingsForm = contractSchema<{ settings: NetworkSettingsFields }>(
  z.object({ settings: NetworkUpdateBody.shape.settings }),
);

/** The network's settings: what players see before and while joining, where they land, and its runtimes. */
export function NetworkPage() {
  const { t } = useTranslation("web");
  const network = useNetworkGet();
  if (network.data === undefined) {
    return (
      <DetailFallback
        error={network.error}
        icon={Globe}
        goneTitle={t("network.failed")}
        goneDescription=""
        failedTitle={t("network.failed")}
        back={
          <Link to="/" className="font-label text-accent-text">
            {t("errors.back")}
          </Link>
        }
      />
    );
  }
  return (
    <NetworkEditor key={network.data.data.version} view={network.data.data} />
  );
}

type NetworkAction = "restart" | "rotate" | "maintenance";

function NetworkEditor({ view }: { view: NetworkSettingsView }) {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const client = useQueryClient();
  const canWrite = useCan("network:write");
  const me = useMeGet();
  const overview = useOverviewGet();
  const update = useNetworkUpdate();
  const maintenance = useNetworkMaintenance();
  const restart = useNetworkRestart();
  const rotate = useNetworkRotateForwardingToken();
  const eula = useNetworkAcceptEula();
  const pending = usePendingAction<NetworkAction, true>();
  const saved = useSaved(getNetworkGetQueryKey());
  const saveFailed = useSaveFailure(() =>
    client.invalidateQueries({ queryKey: getNetworkGetQueryKey() }),
  );
  const validators = {
    motd: useTextValidator("MOTD"),
    maintenanceMotd: useTextValidator("MAINTENANCE_MOTD"),
    versionName: useTextValidator("VERSION_NAME"),
    maintenanceVersionName: useTextValidator("MAINTENANCE_VERSION_NAME"),
    maintenanceKickMessage: useTextValidator("MAINTENANCE_KICK_MESSAGE"),
    tablistHeader: useTextValidator("TABLIST_HEADER"),
    tablistFooter: useTextValidator("TABLIST_FOOTER"),
  };
  const groupIds = useBackendGroupIds();

  const { form, changes, saving } = useSettingsForm({
    settings: view.settings,
    schema: SettingsForm,
    save: (settings) =>
      update
        .mutateAsync({
          data: {
            settings: { ...settings, maintenance: view.settings.maintenance },
            version: view.version,
          },
        })
        .then(saved, saveFailed),
  });
  const settings = useStore(form.store, (state) => state.values.settings);
  const started = (message: string) => () => toast({ message });

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("nav.network")}
        description={
          view.settings.maintenance
            ? t("overview.maintenanceOn")
            : t("overview.maintenanceOff")
        }
        actions={
          canWrite ? (
            <>
              <MoreMenu
                groups={[
                  [
                    ...(view.settings.maintenance
                      ? [
                          {
                            label: t("network.restart.action"),
                            icon: RotateCw,
                            onSelect: () => pending.open("restart", true),
                          },
                        ]
                      : []),
                    {
                      label: t("network.rotate.action"),
                      icon: KeyRound,
                      onSelect: () => pending.open("rotate", true),
                    },
                  ],
                ]}
              />
              {view.settings.maintenance ? (
                <Button
                  icon={Play}
                  loading={maintenance.isPending}
                  onClick={() =>
                    maintenance.mutate(false, {
                      onSuccess: started(t("overview.maintenanceEnded")),
                      onError: failed,
                    })
                  }
                >
                  {t("overview.endMaintenance")}
                </Button>
              ) : (
                <Button
                  icon={Construction}
                  onClick={() => pending.open("maintenance", true)}
                >
                  {t("overview.startMaintenance")}
                </Button>
              )}
            </>
          ) : undefined
        }
      />
      {view.eulaAcceptedAt == null ? (
        <Note
          tone="warning"
          icon={FileWarning}
          action={
            me.data?.data.roleId === OWNER ? (
              <Button
                size="sm"
                loading={eula.isPending}
                onClick={() =>
                  eula.mutate(undefined, {
                    onSuccess: (answer) => {
                      saved(answer);
                    },
                    onError: failed,
                  })
                }
              >
                {t("network.eula.accept")}
              </Button>
            ) : undefined
          }
        >
          <span className="font-label">{t("overview.eula")}</span>{" "}
          {t("network.eula.description")}{" "}
          <a
            href="https://aka.ms/MinecraftEULA"
            target="_blank"
            rel="noreferrer"
            className="text-accent-text underline-offset-2 hover:underline"
          >
            {t("network.eula.read")}
          </a>
        </Note>
      ) : null}
      <EditorForm
        form={form}
        canWrite={canWrite}
        changes={changes}
        saving={saving}
        aside={
          <>
            <Card title={t("permissions.look.title")}>
              <NetworkLook
                settings={settings}
                online={overview.data?.data.playersOnline ?? 0}
              />
            </Card>
            {view.eulaAcceptedAt == null ? null : (
              <p className="px-1 text-small text-text-3">
                {t("network.eula.accepted")} <Ago at={view.eulaAcceptedAt} />
              </p>
            )}
          </>
        }
      >
        <Card title={t("network.form.serverList")}>
          <div className="flex flex-col gap-3">
            <form.AppField name="settings.motd" validators={validators.motd}>
              {(field) => (
                <field.TagTextFormField
                  multiline
                  field="MOTD"
                  label={t("network.form.motd")}
                  hint={t("network.form.motdHint")}
                />
              )}
            </form.AppField>
            <div className="grid grid-cols-[minmax(0,1fr)_160px] gap-4">
              <form.AppField
                name="settings.versionName"
                validators={validators.versionName}
              >
                {(field) => (
                  <field.TagTextFormField
                    field="VERSION_NAME"
                    label={t("network.form.versionName")}
                    hint={t("network.form.versionNameHint")}
                  />
                )}
              </form.AppField>
              <form.AppField name="settings.maxPlayers">
                {(field) => (
                  <field.NumberField
                    label={t("network.form.maxPlayers")}
                    hint={t("network.form.maxPlayersHint")}
                  />
                )}
              </form.AppField>
            </div>
          </div>
        </Card>
        <Card title={t("network.form.maintenance")}>
          <div className="flex flex-col gap-3">
            <form.AppField
              name="settings.maintenanceMotd"
              validators={validators.maintenanceMotd}
            >
              {(field) => (
                <field.TagTextFormField
                  multiline
                  field="MAINTENANCE_MOTD"
                  label={t("network.form.motd")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.maintenanceVersionName"
              validators={validators.maintenanceVersionName}
            >
              {(field) => (
                <field.TagTextFormField
                  field="MAINTENANCE_VERSION_NAME"
                  label={t("network.form.versionName")}
                  hint={t("network.form.maintenanceVersionHint")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.maintenanceKickMessage"
              validators={validators.maintenanceKickMessage}
            >
              {(field) => (
                <field.TagTextFormField
                  field="MAINTENANCE_KICK_MESSAGE"
                  label={t("network.form.kickMessage")}
                  hint={t("network.form.kickMessageHint")}
                />
              )}
            </form.AppField>
          </div>
        </Card>
        <Card title={t("network.form.tablist")}>
          <div className="flex flex-col gap-3">
            <form.AppField name="settings.tablistEnabled">
              {(field) => (
                <field.SwitchField
                  label={t("network.form.tablistEnabled")}
                  description={t("network.form.tablistEnabledHint")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.tablistHeader"
              validators={validators.tablistHeader}
            >
              {(field) => (
                <field.TagTextFormField
                  multiline
                  field="TABLIST_HEADER"
                  label={t("network.form.header")}
                />
              )}
            </form.AppField>
            <form.AppField
              name="settings.tablistFooter"
              validators={validators.tablistFooter}
            >
              {(field) => (
                <field.TagTextFormField
                  multiline
                  field="TABLIST_FOOTER"
                  label={t("network.form.footer")}
                />
              )}
            </form.AppField>
          </div>
        </Card>
        <Card title={t("network.form.players")}>
          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-2 gap-4">
              <form.AppField name="settings.defaultGroupId">
                {(field) => (
                  <field.SelectField
                    label={t("network.form.defaultGroup")}
                    hint={t("network.form.defaultGroupHint")}
                    options={groupIds.map((id) => ({ value: id, label: id }))}
                  />
                )}
              </form.AppField>
              <form.AppField name="settings.routingStrategy">
                {(field) => (
                  <field.SelectField
                    label={t("network.form.routing")}
                    hint={t(`network.routing.${field.state.value}`)}
                    options={ROUTING_STRATEGIES.map((strategy) => ({
                      value: strategy,
                      label: t(`network.routing.names.${strategy}`),
                    }))}
                  />
                )}
              </form.AppField>
            </div>
            <form.AppField name="settings.permissionSystemEnabled">
              {(field) => (
                <field.SwitchField
                  label={t("network.form.permissions")}
                  description={t("network.form.permissionsHint")}
                />
              )}
            </form.AppField>
            <form.AppField name="settings.requireMinecraftLink">
              {(field) => (
                <field.SwitchField
                  label={t("network.form.link")}
                  description={t("network.form.linkHint")}
                />
              )}
            </form.AppField>
          </div>
        </Card>
        <RuntimeCard />
      </EditorForm>
      <ConfirmDialog
        open={pending.isOpen("maintenance")}
        onOpenChange={pending.onOpenChange}
        title={t("overview.maintenanceTitle")}
        description={t("overview.maintenanceDescription")}
        confirmLabel={t("overview.startMaintenance")}
        onConfirm={() =>
          maintenance
            .mutateAsync(true)
            .then(started(t("overview.maintenanceStarted")), failed)
        }
      />
      <ConfirmDialog
        open={pending.isOpen("restart")}
        onOpenChange={pending.onOpenChange}
        title={t("network.restart.title")}
        description={t("network.restart.description")}
        confirmLabel={t("network.restart.action")}
        typedName={t("network.typed")}
        onConfirm={() =>
          restart
            .mutateAsync({ data: {} })
            .then(started(t("network.restart.started")), failed)
        }
      />
      <ConfirmDialog
        open={pending.isOpen("rotate")}
        onOpenChange={pending.onOpenChange}
        title={t("network.rotate.title")}
        description={t("network.rotate.description")}
        confirmLabel={t("network.rotate.action")}
        typedName={t("network.typed")}
        onConfirm={() =>
          rotate
            .mutateAsync()
            .then(started(t("network.rotate.started")), failed)
        }
      />
    </div>
  );
}
