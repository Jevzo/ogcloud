import { FiClock, FiXCircle } from "react-icons/fi";
import { MdPublishedWithChanges } from "react-icons/md";

import type { ServerActionKind } from "@/types/server";

interface ServerActionButtonsProps {
  serverId: string;
  serverType?: string;
  activeActionKey: string | null;
  onAction: (serverId: string, action: ServerActionKind) => void | Promise<void>;
  iconOnly?: boolean;
}

const getActionLabel = (action: ServerActionKind) => {
  if (action === "drain") {
    return "Drain gracefully";
  }

  if (action === "push") {
    return "Force template push";
  }

  return "Kill";
};

const getActionButtonClassName = (action: ServerActionKind) => {
  if (action === "drain") {
    return "button-hover-lift button-shadow-warning inline-flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/10 text-amber-300 disabled:cursor-not-allowed disabled:opacity-60";
  }

  if (action === "push") {
    return "button-hover-lift button-shadow-primary inline-flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary disabled:cursor-not-allowed disabled:opacity-60";
  }

  return "button-hover-lift button-shadow-danger inline-flex h-8 w-8 items-center justify-center rounded-lg bg-red-500/10 text-red-300 disabled:cursor-not-allowed disabled:opacity-60";
};

const getActionIcon = (action: ServerActionKind) => {
  if (action === "drain") {
    return <FiClock className="h-4 w-4" />;
  }

  if (action === "push") {
    return <MdPublishedWithChanges className="h-4 w-4" />;
  }

  return <FiXCircle className="h-4 w-4" />;
};

const ACTION_ORDER: ServerActionKind[] = ["drain", "push", "kill"];

const ServerActionButtons = ({
  serverId,
  serverType,
  activeActionKey,
  onAction,
  iconOnly = true,
}: ServerActionButtonsProps) => {
  const isActionInProgress = activeActionKey !== null;
  const visibleActions =
    serverType?.toUpperCase() === "PROXY"
      ? ACTION_ORDER.filter((action) => action !== "push")
      : ACTION_ORDER;

  return (
    <div
      className={`flex items-center gap-1.5 ${
        iconOnly ? "justify-end" : "justify-center"
      }`}
    >
      {visibleActions.map((action) => {
        const actionKey = `${serverId}:${action}`;
        const label = getActionLabel(action);
        const isBusy = activeActionKey === actionKey;

        return (
          <button
            key={action}
            type="button"
            disabled={isActionInProgress}
            onClick={() => void onAction(serverId, action)}
            className={
              iconOnly
                ? getActionButtonClassName(action)
                : `${getActionButtonClassName(action)} app-button-field w-auto gap-2 px-3 py-2 text-sm font-medium`
            }
            title={iconOnly ? label : undefined}
            aria-label={isBusy ? `${label} in progress` : label}
            aria-busy={isBusy}
          >
            {getActionIcon(action)}
            {!iconOnly && <span>{isBusy ? `${label}...` : label}</span>}
            {iconOnly ? (
              <span className="sr-only">{isBusy ? `${label} in progress` : label}</span>
            ) : null}
          </button>
        );
      })}
    </div>
  );
};

export default ServerActionButtons;
