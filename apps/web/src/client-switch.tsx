import { Segmented } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";

/** Which clients a preview shows a text for: 1.16+, or older ones in nearest colours (D-0204). */
export type Client = "modern" | "legacy";

export function ClientSwitch({
  value,
  onValueChange,
}: {
  value: Client;
  onValueChange: (client: Client) => void;
}) {
  const { t } = useTranslation("web");
  return (
    <Segmented
      label={t("permissions.look.client")}
      options={[
        { value: "modern", label: t("permissions.look.modern") },
        { value: "legacy", label: t("permissions.look.legacy") },
      ]}
      value={value}
      onValueChange={onValueChange}
    />
  );
}
