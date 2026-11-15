import { useMeUnlinkMinecraft } from "@ogcloud/api-client/react";
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  CopyField,
  Segmented,
  useToast,
} from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Check, KeyRound, Unlink } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Initials, PlayerHead } from "../../avatar.tsx";
import { DetailRow, Details } from "../../details.tsx";
import { useConfirmFailure } from "../../errors.ts";
import { useMe } from "../../permissions.ts";
import { type ThemeChoice, usePreferences } from "../../preferences.ts";
import { useSession } from "../../session-context.ts";
import { LinkFlow } from "../../shell/link-required.tsx";
import { heldAreas, isAction, isArea } from "../roles/permission-picker.tsx";
import { useRoleNames } from "../users/role-names.ts";

// The head is the Minecraft card's picture, as large as the name beside it is tall with its caption.
const HEAD_SIZE = 56;

/** Who the user is; names, emails and roles are changed by an administrator (D-0094). */
export function ProfileCard() {
  const { t } = useTranslation("web");
  const me = useMe();
  const roles = useRoleNames();
  return (
    <Card title={t("account.profile.title")}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-4">
          <Initials name={me.username} className="size-14 text-heading" />
          <div className="flex min-w-0 flex-1 flex-col">
            <span className="truncate text-heading">{me.username}</span>
            <span className="truncate text-text-2">{me.email}</span>
          </div>
          <Badge tone="neutral" icon={KeyRound}>
            {roles.nameOf(me.roleId)}
          </Badge>
        </div>
        <div className="flex flex-col gap-1.5">
          <span className="font-label">{t("account.profile.userId")}</span>
          <CopyField value={me.id} label={t("account.profile.userId")} />
        </div>
        <p className="text-small text-text-3">{t("account.profile.byAdmin")}</p>
      </div>
    </Card>
  );
}

/** What the user's role lets them do, area by area (D-0092); the role itself is an owner's to change. */
export function AccessCard() {
  const { t } = useTranslation("web");
  const me = useMe();
  const areas = heldAreas(me.permissions);
  return (
    <Card title={t("account.access.title")}>
      <ul className="flex flex-col">
        {areas.map(([area, actions]) => (
          <li
            key={area}
            className="flex items-center gap-4 border-border-subtle border-t py-2.5 first:border-t-0 first:pt-0 last:pb-0"
          >
            <div className="min-w-0 flex-1">
              <div className="font-label">
                {isArea(area) ? t(`access.areas.${area}.name`) : area}
              </div>
              {isArea(area) ? (
                <div className="text-meta text-text-3">
                  {t(`access.areas.${area}.description`)}
                </div>
              ) : null}
            </div>
            <div className="flex shrink-0 flex-wrap justify-end gap-1.5">
              {actions.map((action) => (
                <Badge key={action} tone="neutral" icon={Check}>
                  {isAction(action) ? t(`access.actions.${action}`) : action}
                </Badge>
              ))}
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}

/** The user's Minecraft account: linked through an in-game code, or unlinked again (D-0106). */
export function MinecraftCard() {
  const { t } = useTranslation("web");
  const me = useMe();
  const toast = useToast();
  const keepOpen = useConfirmFailure();
  const session = useSession();
  const queries = useQueryClient();
  const [unlinking, setUnlinking] = useState(false);
  const unlink = useMeUnlinkMinecraft();
  return (
    <Card title={t("account.minecraft.title")}>
      {me.linkedPlayerUuid == null ? (
        <div className="flex flex-col gap-3">
          <p className="text-text-2">{t("account.minecraft.unlinked")}</p>
          <LinkFlow />
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-4">
            <PlayerHead uuid={me.linkedPlayerUuid} size={HEAD_SIZE} />
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="truncate font-mono text-heading">
                {me.linkedPlayerName}
              </span>
              <span className="text-meta text-text-3">
                {t("account.minecraft.linked")}
              </span>
            </span>
            <Button icon={Unlink} onClick={() => setUnlinking(true)}>
              {t("account.minecraft.unlink")}
            </Button>
          </div>
          <div className="flex flex-col gap-1.5">
            <span className="font-label">{t("account.minecraft.uuid")}</span>
            <CopyField
              value={me.linkedPlayerUuid}
              label={t("account.minecraft.uuid")}
            />
          </div>
        </div>
      )}
      <ConfirmDialog
        open={unlinking}
        onOpenChange={setUnlinking}
        title={t("account.minecraft.unlinkTitle")}
        description={t("account.minecraft.unlinkDescription")}
        confirmLabel={t("account.minecraft.unlink")}
        onConfirm={() =>
          unlink.mutateAsync().then(() => {
            toast({ message: t("account.minecraft.unlinkDone") });
            // Permissions that depend on the link change with the next token (D-0106).
            session?.forgetAccessToken();
            void queries.invalidateQueries();
          }, keepOpen)
        }
      />
    </Card>
  );
}

/** The theme, remembered on this device; set here only (D-0235). */
export function AppearanceCard() {
  const { t } = useTranslation("web");
  const { preferences, change } = usePreferences();
  return (
    <Card title={t("account.appearance.title")}>
      <div className="flex flex-col gap-3">
        <Details>
          <DetailRow label={t("shell.theme.label")}>
            <Segmented
              label={t("shell.theme.label")}
              options={(["system", "light", "dark"] as const).map((value) => ({
                value,
                label: t(`shell.theme.${value}`),
              }))}
              value={preferences.theme}
              onValueChange={(theme: ThemeChoice) =>
                change({ ...preferences, theme })
              }
            />
          </DetailRow>
        </Details>
        <p className="text-small text-text-3">
          {t("account.appearance.device")}
        </p>
      </div>
    </Card>
  );
}
