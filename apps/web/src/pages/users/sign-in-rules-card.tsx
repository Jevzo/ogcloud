import {
  getSignInRuleGetQueryKey,
  useSignInRuleGet,
  useSignInRuleUpdate,
} from "@ogcloud/api-client/react";
import { Card, ConfirmDialog, Loading, Switch, useToast } from "@ogcloud/ui";
import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  loadFailure,
  useConfirmFailure,
  useFailureToast,
} from "../../errors.ts";
import { LoadFailed } from "../../load-failed.tsx";
import { useIsOwner, useMe } from "../../permissions.ts";

/** Who may use the dashboard beyond holding a role (D-0229): owners change it, other user managers see it. */
export function SignInRulesCard() {
  const { t } = useTranslation("web");
  const toast = useToast();
  const failed = useFailureToast();
  const keepOpen = useConfirmFailure();
  const queries = useQueryClient();
  const isOwner = useIsOwner();
  const me = useMe();
  const rules = useSignInRuleGet();
  const update = useSignInRuleUpdate();
  const [confirming, setConfirming] = useState(false);
  const failure = loadFailure(rules);

  const save = (requireMinecraftLink: boolean) =>
    update.mutateAsync({ data: { requireMinecraftLink } }).then(() => {
      toast({
        message: t(
          requireMinecraftLink
            ? "users.signInRules.required"
            : "users.signInRules.notRequired",
        ),
      });
      void queries.invalidateQueries({ queryKey: getSignInRuleGetQueryKey() });
    });

  return (
    <Card title={t("users.signInRules.title")}>
      {failure !== undefined && rules.data === undefined ? (
        <LoadFailed failure={failure} />
      ) : (
        <Loading when={rules.data === undefined}>
          <Switch
            label={t("users.signInRules.link")}
            description={t(
              isOwner
                ? "users.signInRules.linkHint"
                : "users.signInRules.ownersOnly",
            )}
            checked={rules.data?.data.requireMinecraftLink ?? false}
            disabled={!isOwner || update.isPending}
            // Turning it on can shut people out, the owner included, so it asks first; turning it off does not.
            onCheckedChange={(required) =>
              required ? setConfirming(true) : void save(false).catch(failed)
            }
          />
        </Loading>
      )}
      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title={t("users.signInRules.confirmTitle")}
        description={t(
          me.linkedPlayerName == null
            ? "users.signInRules.confirmUnlinked"
            : "users.signInRules.confirm",
        )}
        confirmLabel={t("users.signInRules.confirmLabel")}
        onConfirm={() => save(true).then(() => undefined, keepOpen)}
      />
    </Card>
  );
}
