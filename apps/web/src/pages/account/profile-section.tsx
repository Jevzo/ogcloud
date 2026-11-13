import { useMeUnlinkMinecraft } from "@ogcloud/api-client/react";
import type { DensityName } from "@ogcloud/tokens";
import { Button, Card, ConfirmDialog, Segmented, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { Unlink } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { DetailRow, Details } from "../../details.tsx";
import { useConfirmFailure } from "../../errors.ts";
import { useMe } from "../../permissions.ts";
import { type ThemeChoice, usePreferences } from "../../preferences.ts";
import { useSession } from "../../session-context.ts";
import { LinkFlow } from "../../shell/link-required.tsx";
import { useRoleNames } from "../users/role-names.ts";

/** Who the user is; names and emails are changed by an administrator (D-0094). */
export function ProfileCard() {
  const { t } = useTranslation("web");
  const me = useMe();
  const roles = useRoleNames();
  return (
    <Card title={t("account.profile.title")}>
      <div className="flex flex-col gap-3">
        <Details>
          <DetailRow label={t("users.form.username")}>{me.username}</DetailRow>
          <DetailRow label={t("users.form.email")}>{me.email}</DetailRow>
          <DetailRow label={t("users.form.role")}>
            {roles.nameOf(me.roleId)}
          </DetailRow>
        </Details>
        <p className="text-small text-text-3">{t("account.profile.byAdmin")}</p>
      </div>
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
        <div className="flex items-center gap-3">
          <span className="flex flex-1 flex-col">
            <span className="font-mono text-mono">{me.linkedPlayerName}</span>
            <span className="text-meta text-text-3">
              {t("account.minecraft.linked")}
            </span>
          </span>
          <Button icon={Unlink} onClick={() => setUnlinking(true)}>
            {t("account.minecraft.unlink")}
          </Button>
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

/** Theme and density, remembered on this device (D-0231); the account menu offers the same. */
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
          <DetailRow label={t("shell.density.label")}>
            <Segmented
              label={t("shell.density.label")}
              options={(["compact", "comfortable"] as const).map((value) => ({
                value,
                label: t(`shell.density.${value}`),
              }))}
              value={preferences.density}
              onValueChange={(density: DensityName) =>
                change({ ...preferences, density })
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
