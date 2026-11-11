import { useRolePermissions } from "@ogcloud/api-client/react";
import { Card, Checkbox, cn } from "@ogcloud/ui";
import { useTranslation } from "react-i18next";

// The catalog's areas and actions with labels (D-0092); anything newer shows as the API names it.
const AREAS = [
  "overview",
  "servers",
  "groups",
  "players",
  "network",
  "permissions",
  "templates",
  "npcs",
  "logs",
  "users",
  "roles",
  "oauth-clients",
  "privacy",
] as const;
const ACTIONS = ["read", "write", "command", "export", "erase"] as const;
const READ = "read";
// Each action's column, so the boxes line up down the list: seeing first, then acting on it.
const COLUMN: Record<string, string> = {
  read: "col-start-2",
  export: "col-start-2",
  write: "col-start-3",
  command: "col-start-3",
  erase: "col-start-3",
};

type Area = (typeof AREAS)[number];
type Action = (typeof ACTIONS)[number];

const isArea = (area: string): area is Area =>
  (AREAS as readonly string[]).includes(area);
const isAction = (action: string): action is Action =>
  (ACTIONS as readonly string[]).includes(action);

/** The catalog by area, in the order the API lists it: `servers:read` is the servers area's read action. */
function areasOf(catalog: readonly string[]): [string, string[]][] {
  const areas = new Map<string, string[]>();
  for (const permission of catalog) {
    const [area = permission, action = ""] = permission.split(":");
    areas.set(area, [...(areas.get(area) ?? []), action]);
  }
  return [...areas];
}

interface PermissionPickerProps {
  value: readonly string[];
  onChange: (value: string[]) => void;
  disabled?: boolean;
}

/**
 * Picks API permissions area by area. Any other action in an area also lets it be viewed, and taking the view away
 * takes the rest of that area with it.
 */
export function PermissionPicker({
  value,
  onChange,
  disabled = false,
}: PermissionPickerProps) {
  const { t } = useTranslation("web");
  // Every permission the API knows, in its order.
  const catalog = useRolePermissions().data?.data ?? [];
  const held = new Set(value);

  const toggle = (area: string, action: string, on: boolean) => {
    const next = new Set(held);
    const read = `${area}:${READ}`;
    if (on) {
      next.add(`${area}:${action}`);
      if (action !== READ && catalog.includes(read)) next.add(read);
    } else if (action === READ) {
      for (const permission of catalog) {
        if (permission.startsWith(`${area}:`)) next.delete(permission);
      }
    } else {
      next.delete(`${area}:${action}`);
    }
    onChange(catalog.filter((permission) => next.has(permission)));
  };

  return (
    <fieldset
      aria-label={t("access.title")}
      className="m-0 flex min-w-0 flex-col border-0 p-0"
    >
      {areasOf(catalog).map(([area, actions]) => (
        <div
          key={area}
          className="grid grid-cols-[minmax(0,1fr)_6rem_7.5rem] items-start gap-x-4 border-border-subtle border-t py-2.5 first:border-t-0 first:pt-0"
        >
          <div className="min-w-0">
            <div className="font-label">
              {isArea(area) ? t(`access.areas.${area}.name`) : area}
            </div>
            {isArea(area) ? (
              <div className="text-meta text-text-3">
                {t(`access.areas.${area}.description`)}
              </div>
            ) : null}
          </div>
          {actions.map((action) => (
            <Checkbox
              key={action}
              className={cn("row-start-1 pt-0.5", COLUMN[action])}
              label={isAction(action) ? t(`access.actions.${action}`) : action}
              checked={held.has(`${area}:${action}`)}
              disabled={disabled}
              onCheckedChange={(on) => toggle(area, action, on === true)}
            />
          ))}
        </div>
      ))}
    </fieldset>
  );
}

/** A role's or a client's permissions on its page. */
export function PermissionsCard(props: PermissionPickerProps) {
  const { t } = useTranslation("web");
  return (
    <Card title={t("access.title")}>
      <PermissionPicker {...props} />
    </Card>
  );
}
