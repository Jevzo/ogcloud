import type { GroupViewType } from "@ogcloud/api-client";
import {
  getGroupListQueryKey,
  getNetworkGetQueryKey,
  getOverviewGetQueryKey,
  useGroupCreate,
  useGroupList,
  useMeGet,
  useNetworkAcceptEula,
  useNetworkGet,
  useRuntimeList,
} from "@ogcloud/api-client/react";
import { Button, Checkbox, Note, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Navigate, useNavigate } from "@tanstack/react-router";
import { CircleCheck, ExternalLink, Layers } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";
import { useFormat } from "../../format.ts";
import { MINECRAFT_EULA_URL } from "../../links.ts";
import { isOwner } from "../../permissions.ts";
import { invalidatePaths } from "../../queries.ts";
import { newGroupSettings } from "../groups/group-defaults.ts";
import { SetupFrame, StepActions, StepHeading } from "./setup-frame.tsx";

/** The groups a network needs to take its first players: a proxy and the lobby they land in. */
const STARTERS: readonly { id: "proxy" | "lobby"; type: GroupViewType }[] = [
  { id: "proxy", type: "PROXY" },
  { id: "lobby", type: "DYNAMIC" },
];

/** The new owner's last setup steps, signed in: the Minecraft EULA (D-0046, D-0100) and the starter groups. */
export function WelcomePage() {
  const me = useMeGet();
  const [step, setStep] = useState<"eula" | "groups">("eula");
  if (me.data === undefined) return <SetupFrame step="eula">{null}</SetupFrame>;
  if (!isOwner(me.data.data)) return <Navigate to="/" />;
  return (
    <SetupFrame step={step}>
      {step === "eula" ? (
        <EulaStep onNext={() => setStep("groups")} />
      ) : (
        <GroupsStep />
      )}
    </SetupFrame>
  );
}

function EulaStep({ onNext }: { onNext: () => void }) {
  const { t } = useTranslation("web");
  const format = useFormat();
  const failed = useFailureToast();
  const queries = useQueryClient();
  const network = useNetworkGet();
  const [agreed, setAgreed] = useState(false);
  const accept = useNetworkAcceptEula();
  const acceptedAt = network.data?.data.eulaAcceptedAt ?? null;

  return (
    <>
      <StepHeading
        title={t("setup.eula.title")}
        description={t("setup.eula.description")}
      />
      <a
        href={MINECRAFT_EULA_URL}
        target="_blank"
        rel="noreferrer"
        className="inline-flex w-fit items-center gap-1.5 font-label text-accent hover:underline"
      >
        {t("setup.eula.read")}
        <ExternalLink aria-hidden="true" size={14} />
      </a>
      {acceptedAt === null ? (
        <Checkbox
          label={t("setup.eula.agree")}
          description={t("setup.eula.agreeHint")}
          checked={agreed}
          onCheckedChange={(checked) => setAgreed(checked === true)}
        />
      ) : (
        <Note tone="success" icon={CircleCheck}>
          {t("setup.eula.accepted", {
            ago: format.ago(new Date(acceptedAt), Date.now()),
          })}
        </Note>
      )}
      <StepActions>
        {acceptedAt === null ? (
          <Button
            kind="primary"
            disabled={!agreed}
            loading={accept.isPending}
            onClick={() =>
              accept.mutate(undefined, {
                onSuccess: () => {
                  void invalidatePaths(queries, [
                    getNetworkGetQueryKey()[0],
                    getOverviewGetQueryKey()[0],
                  ]);
                  onNext();
                },
                onError: failed,
              })
            }
          >
            {t("setup.eula.accept")}
          </Button>
        ) : (
          <Button kind="primary" onClick={onNext}>
            {t("setup.next")}
          </Button>
        )}
      </StepActions>
    </>
  );
}

function GroupsStep() {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const navigate = useNavigate();
  const queries = useQueryClient();
  const groups = useGroupList();
  const runtime = useRuntimeList();
  const create = useGroupCreate();
  const existing = new Set((groups.data?.data ?? []).map((group) => group.id));
  const missing = STARTERS.filter((starter) => !existing.has(starter.id));
  const [chosen, setChosen] = useState<ReadonlySet<string>>(
    () => new Set(STARTERS.map((starter) => starter.id)),
  );
  const [creating, setCreating] = useState(false);
  const profiles = runtime.data?.data ?? [];
  const profileOf = (type: GroupViewType) =>
    newGroupSettings(type, profiles).profileId;
  const finish = () => {
    toast({ message: t("setup.groups.ready") });
    void navigate({ to: "/" });
  };

  const toCreate = missing.filter((candidate) => chosen.has(candidate.id));
  const createChosen = async () => {
    setCreating(true);
    try {
      for (const starter of toCreate) {
        await create.mutateAsync({
          data: {
            id: starter.id,
            type: starter.type,
            settings: newGroupSettings(starter.type, profiles),
          },
        });
      }
      finish();
    } catch (error) {
      failed(error);
    } finally {
      // Also after a partial failure, so a retry skips the groups that were created.
      await invalidatePaths(queries, [
        getGroupListQueryKey()[0],
        getOverviewGetQueryKey()[0],
      ]);
      setCreating(false);
    }
  };

  return (
    <>
      <StepHeading
        title={t("setup.groups.title")}
        description={t("setup.groups.description")}
      />
      <div className="flex flex-col gap-3">
        {STARTERS.map((starter) => (
          <div
            key={starter.id}
            className="flex items-start gap-3 rounded-control border border-border-subtle p-3"
          >
            <Layers
              aria-hidden="true"
              size={18}
              className="mt-0.5 shrink-0 text-text-2"
            />
            <Checkbox
              className="flex-1"
              label={t(`setup.groups.${starter.id}`, {
                profile: profileOf(starter.type),
              })}
              description={
                existing.has(starter.id)
                  ? t("setup.groups.exists")
                  : t(`setup.groups.${starter.id}Hint`)
              }
              checked={existing.has(starter.id) || chosen.has(starter.id)}
              disabled={existing.has(starter.id)}
              onCheckedChange={(checked) =>
                setChosen((current) => {
                  const next = new Set(current);
                  if (checked === true) next.add(starter.id);
                  else next.delete(starter.id);
                  return next;
                })
              }
            />
          </div>
        ))}
      </div>
      <p className="text-small text-text-3">{t("setup.groups.later")}</p>
      <StepActions>
        {toCreate.length === 0 ? (
          <Button kind="primary" onClick={finish}>
            {t("setup.groups.finish")}
          </Button>
        ) : (
          <>
            <Button kind="ghost" onClick={finish}>
              {t("setup.groups.skip")}
            </Button>
            <Button
              kind="primary"
              loading={creating}
              disabled={profiles.length === 0}
              onClick={() => void createChosen()}
            >
              {t("setup.groups.create")}
            </Button>
          </>
        )}
      </StepActions>
    </>
  );
}
