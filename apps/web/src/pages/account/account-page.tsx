import { PageHeader, Tabs } from "@ogcloud/ui";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { LoginsCard } from "./logins-card.tsx";
import { PasswordCard } from "./password-card.tsx";
import {
  AccessCard,
  AppearanceCard,
  MinecraftCard,
  ProfileCard,
} from "./profile-section.tsx";
import { TwoFactorCard } from "./two-factor-card.tsx";

type Section = "profile" | "security";
// Two columns across the full width, each stacking its own cards so they keep their height (D-0235).
const COLUMNS = "grid grid-cols-2 items-start gap-6";
const COLUMN = "flex min-w-0 flex-col gap-6";

/** The signed-in user's own account: profile, access, Minecraft link and appearance; password, factors and devices. */
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
              <div className={COLUMNS}>
                <div className={COLUMN}>
                  <ProfileCard />
                  <MinecraftCard />
                  <AppearanceCard />
                </div>
                <AccessCard />
              </div>
            ),
          },
          {
            value: "security",
            label: t("account.tabs.security"),
            content: (
              <div className={COLUMNS}>
                <div className={COLUMN}>
                  <PasswordCard />
                </div>
                <div className={COLUMN}>
                  <TwoFactorCard />
                  <LoginsCard />
                </div>
              </div>
            ),
          },
        ]}
      />
    </div>
  );
}
