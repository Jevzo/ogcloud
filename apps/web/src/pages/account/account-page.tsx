import { PageHeader, Tabs } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { LoginsCard } from "./logins-card.tsx";
import { PasswordCard } from "./password-card.tsx";
import {
  AppearanceCard,
  MinecraftCard,
  ProfileCard,
} from "./profile-section.tsx";
import { TwoFactorCard } from "./two-factor-card.tsx";

type Section = "profile" | "security";

/** The signed-in user's own account: profile, Minecraft link and appearance; password, factors and devices. */
export function AccountPage() {
  const { t } = useTranslation("web");
  const [section, setSection] = useState<Section>("profile");
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("account.title")} />
      <Tabs
        label={t("account.sections")}
        value={section}
        onValueChange={setSection}
        tabs={[
          {
            value: "profile",
            label: t("account.tabs.profile"),
            content: (
              <div className="flex max-w-form flex-col gap-6">
                <ProfileCard />
                <MinecraftCard />
                <AppearanceCard />
              </div>
            ),
          },
          {
            value: "security",
            label: t("account.tabs.security"),
            content: (
              <div className="flex max-w-form flex-col gap-6">
                <TwoFactorCard />
                <LoginsCard />
                <PasswordCard />
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
