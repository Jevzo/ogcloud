import { useEffect, useState } from "react";
import { motion } from "motion/react";
import { FiX } from "react-icons/fi";

import AppNumberInput from "@/components/AppNumberInput";
import AppSelect from "@/components/AppSelect";
import FieldHintLabel from "@/components/FieldHintLabel";
import AppToasts from "@/components/AppToasts";
import { listGroups, requestServerForGroup } from "@/lib/api";
import type { GroupListItem } from "@/types/dashboard";

interface DeployServerModalProps {
  isOpen: boolean;
  onClose: () => void;
  getAccessToken: () => Promise<string>;
  onSuccess: (message: string) => void;
  onRequested?: () => Promise<void> | void;
}

type DeployServerModalContentProps = Omit<DeployServerModalProps, "isOpen">;

const MIN_DEPLOY_COUNT = 1;
const MAX_DEPLOY_COUNT = 25;

const getGroupModeTone = (mode: string) =>
  mode.toUpperCase() === "STATIC"
    ? "bg-amber-400 text-slate-950"
    : "bg-primary text-slate-950";

const DeployServerModalContent = ({
  onClose,
  getAccessToken,
  onSuccess,
  onRequested,
}: DeployServerModalContentProps) => {
  const [availableGroups, setAvailableGroups] = useState<GroupListItem[]>([]);
  const [isLoadingGroups, setIsLoadingGroups] = useState(false);
  const [deployError, setDeployError] = useState<string | null>(null);
  const [deployingGroupId, setDeployingGroupId] = useState<string | null>(null);
  const [selectedDeployGroupId, setSelectedDeployGroupId] = useState("");
  const [deployCount, setDeployCount] = useState(MIN_DEPLOY_COUNT);

  useEffect(() => {
    let active = true;

    const loadGroups = async () => {
      setIsLoadingGroups(true);
      setDeployError(null);

      try {
        const accessToken = await getAccessToken();
        const groups = await listGroups(accessToken);

        if (!active) {
          return;
        }

        setAvailableGroups(groups);
        setSelectedDeployGroupId(groups[0]?.id ?? "");
      } catch (error) {
        if (!active) {
          return;
        }

        setDeployError(error instanceof Error ? error.message : "Unable to load groups.");
      } finally {
        if (active) {
          setIsLoadingGroups(false);
        }
      }
    };

    void loadGroups();

    return () => {
      active = false;
    };
  }, [getAccessToken]);

  const handleClose = () => {
    if (deployingGroupId !== null) {
      return;
    }

    onClose();
  };

  const handleDeployGroup = async () => {
    if (!selectedDeployGroupId) {
      setDeployError("Select a group first.");
      return;
    }

    if (!Number.isFinite(deployCount) || deployCount < MIN_DEPLOY_COUNT) {
      setDeployError("Enter a valid amount to request.");
      return;
    }

    setDeployError(null);
    setDeployingGroupId(selectedDeployGroupId);

    try {
      const accessToken = await getAccessToken();
      await requestServerForGroup(accessToken, selectedDeployGroupId, deployCount);

      if (onRequested) {
        await onRequested();
      }

      onSuccess(
        `Requested ${deployCount} new ${selectedDeployGroupId} instance${
          deployCount === 1 ? "" : "s"
        }.`
      );
      onClose();
    } catch (error) {
      setDeployError(
        error instanceof Error ? error.message : "Unable to request a new instance."
      );
      setDeployingGroupId(null);
    }
  };

  const selectedGroup =
    availableGroups.find((group) => group.id === selectedDeployGroupId) ?? null;

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
            <h3 className="text-base font-semibold text-white">Deploy New Instance</h3>
            <p className="text-sm text-slate-400">
              Pick a group and request a new server instance.
            </p>
          </div>
          <button
            type="button"
            onClick={handleClose}
            disabled={deployingGroupId !== null}
            className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
            aria-label="Close"
          >
            <FiX className="h-4 w-4" />
          </button>
        </div>

        <div className="space-y-4 p-6">
          <AppToasts
            items={
              deployError
                ? [
                    {
                      id: "deploy-modal-error",
                      message: deployError,
                      onDismiss: () => setDeployError(null),
                      tone: "error" as const,
                    },
                  ]
                : []
            }
          />

          {isLoadingGroups ? (
            <div className="rounded-lg border border-slate-800 bg-slate-800/40 px-4 py-8 text-center text-sm text-slate-400">
              Loading available groups...
            </div>
          ) : (
            <div className="space-y-4">
              <AppSelect
                id="deploy-group"
                label="Select Group"
                labelHint="Choose the group that should receive the new server instances."
                value={selectedDeployGroupId}
                onChangeValue={setSelectedDeployGroupId}
                disabled={deployingGroupId !== null || availableGroups.length === 0}
              >
                {availableGroups.length === 0 ? (
                  <option value="">No groups available</option>
                ) : (
                  availableGroups.map((group) => (
                    <option key={group.id} value={group.id}>
                      {group.id}
                    </option>
                  ))
                )}
              </AppSelect>

              {selectedGroup ? (
                <div className="rounded-lg border border-slate-800 bg-slate-800/40 px-4 py-3">
                  <div className="flex items-center justify-between gap-3">
                    <span className="text-sm font-semibold text-white">{selectedGroup.id}</span>
                    <span
                      className={`rounded px-2 py-0.5 text-[10px] font-bold uppercase ${getGroupModeTone(
                        selectedGroup.type
                      )}`}
                    >
                      {selectedGroup.type}
                    </span>
                  </div>
                  <p className="mt-1 text-xs text-slate-400">
                    {selectedGroup.maintenance
                      ? "Maintenance currently enabled"
                      : "Available for new instances"}
                  </p>
                </div>
              ) : null}

              <div className="app-field-stack">
                <FieldHintLabel
                  label="Amount"
                  hint="How many new instances should be requested in one action."
                />
                <AppNumberInput
                  id="deploy-count"
                  value={String(deployCount)}
                  min={MIN_DEPLOY_COUNT}
                  max={MAX_DEPLOY_COUNT}
                  step={1}
                  onChangeValue={(nextValue) => {
                    const parsedValue = Number.parseInt(nextValue, 10);
                    setDeployCount(
                      Number.isNaN(parsedValue) ? MIN_DEPLOY_COUNT : parsedValue
                    );
                  }}
                />
              </div>

              <div className="flex justify-end gap-3">
                <button
                  type="button"
                  onClick={handleClose}
                  disabled={deployingGroupId !== null}
                  className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  disabled={deployingGroupId !== null || availableGroups.length === 0}
                  onClick={() => void handleDeployGroup()}
                  className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {deployingGroupId
                    ? `Requesting ${deployCount}...`
                    : `Request ${deployCount} Instance${deployCount === 1 ? "" : "s"}`}
                </button>
              </div>
            </div>
          )}
        </div>
      </motion.div>
    </div>
  );
};

const DeployServerModal = ({ isOpen, ...contentProps }: DeployServerModalProps) => {
  if (!isOpen) {
    return null;
  }

  return <DeployServerModalContent {...contentProps} />;
};

export default DeployServerModal;
