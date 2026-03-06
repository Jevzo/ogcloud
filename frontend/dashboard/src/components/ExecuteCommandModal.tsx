import { useState } from "react";
import { motion } from "motion/react";
import { FiTerminal, FiX } from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import { executeCommand } from "@/lib/api";
import type { CommandTargetType } from "@/types/command";

interface ExecuteCommandModalProps {
  isOpen: boolean;
  onClose: () => void;
  getAccessToken: () => Promise<string>;
  onSuccess: (message: string) => void;
  target: string;
  targetType: CommandTargetType;
  title: string;
  description: string;
  submitLabel: string;
  onSent?: () => Promise<void> | void;
}

type ExecuteCommandModalContentProps = Omit<ExecuteCommandModalProps, "isOpen">;

const getScopeLabel = (targetType: CommandTargetType) => {
  if (targetType === "server") {
    return "Single server";
  }

  if (targetType === "group") {
    return "Group broadcast";
  }

  return "Network-wide broadcast";
};

const getSuccessMessage = (target: string, targetType: CommandTargetType) => {
  if (targetType === "server") {
    return `Sent command to ${target}.`;
  }

  if (targetType === "group") {
    return `Sent command to all running servers in ${target}.`;
  }

  return "Sent command to all running servers.";
};

const getTargetLabel = (target: string, targetType: CommandTargetType) => {
  if (targetType === "all") {
    return "All running servers";
  }

  return target;
};

const ExecuteCommandModalContent = ({
  onClose,
  getAccessToken,
  onSuccess,
  target,
  targetType,
  title,
  description,
  submitLabel,
  onSent,
}: ExecuteCommandModalContentProps) => {
  const [commandDraft, setCommandDraft] = useState("");
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);

  const handleClose = () => {
    if (isSending) {
      return;
    }

    onClose();
  };

  const handleSend = async () => {
    const nextCommand = commandDraft.trim();

    if (!nextCommand) {
      setErrorMessage("Enter a command first.");
      return;
    }

    setIsSending(true);
    setErrorMessage(null);

    try {
      const accessToken = await getAccessToken();
      await executeCommand(accessToken, {
        target,
        targetType,
        command: nextCommand,
      });

      if (onSent) {
        await onSent();
      }

      setIsSending(false);
      onSuccess(getSuccessMessage(target, targetType));
      onClose();
      return;
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to send command.");
      setIsSending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
      <motion.div
        initial={{ y: 12, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.25, ease: "easeOut" }}
        className="w-full max-w-xl rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
          <div>
            <h3 className="flex items-center gap-2 text-base font-semibold text-white">
              <FiTerminal className="h-4 w-4 text-primary" />
              {title}
            </h3>
            <p className="mt-1 text-sm text-slate-400">{description}</p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
            aria-label="Close"
          >
            <FiX className="h-4 w-4" />
          </button>
        </div>

        <div className="space-y-4 p-6">
          <AppToasts
            items={
              errorMessage
                ? [
                    {
                      id: "execute-command-modal-error",
                      message: errorMessage,
                      onDismiss: () => setErrorMessage(null),
                      tone: "error" as const,
                    },
                  ]
                : []
            }
          />

          <div className="rounded-lg border border-slate-800 bg-slate-800/40 px-4 py-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span className="text-sm font-semibold text-white">
                {getTargetLabel(target, targetType)}
              </span>
              <span className="rounded px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-primary ring-1 ring-primary/20">
                {getScopeLabel(targetType)}
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-400">
              {targetType === "server"
                ? "The command will be sent directly to this server."
                : targetType === "group"
                  ? "The command will fan out to every running server in this group."
                  : "The command will fan out to every running server in the network."}
            </p>
          </div>

          <div className="app-field-stack">
            <label htmlFor="execute-command-input" className="app-field-label">
              Command
            </label>
            <input
              id="execute-command-input"
              type="text"
              value={commandDraft}
              onChange={(event) => setCommandDraft(event.target.value)}
              disabled={isSending}
              autoFocus
              placeholder="say Server restart in 30 seconds"
              className="app-input-field w-full rounded-lg border border-slate-700 bg-slate-950/60 px-3 py-2.5 text-sm text-slate-100 outline-none transition focus:border-primary/60 focus:ring-2 focus:ring-primary/10 disabled:cursor-not-allowed"
            />
          </div>

          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={handleClose}
              className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
            >
              Cancel
            </button>
            <button
              type="button"
              disabled={isSending}
              onClick={() => void handleSend()}
              className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <FiTerminal className="h-4 w-4" />
              {isSending ? "Sending..." : submitLabel}
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  );
};

const ExecuteCommandModal = ({ isOpen, ...contentProps }: ExecuteCommandModalProps) => {
  if (!isOpen) {
    return null;
  }

  return <ExecuteCommandModalContent {...contentProps} />;
};

export default ExecuteCommandModal;
