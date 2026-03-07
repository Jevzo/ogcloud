import { useCallback, useEffect, useRef, useState } from "react";
import { motion } from "motion/react";
import {
  FiChevronLeft,
  FiChevronRight,
  FiDownload,
  FiFileText,
  FiFolder,
  FiTrash2,
  FiUpload,
  FiX,
} from "react-icons/fi";

import AppCreatableSelect from "@/components/AppCreatableSelect";
import AppSelect from "@/components/AppSelect";
import AppToasts from "@/components/AppToasts";
import TableRefreshButton from "@/components/TableRefreshButton";
import {
  deleteTemplate,
  downloadTemplate,
  listAllServerGroups,
  listGroups,
  listTemplates,
  uploadTemplate,
} from "@/lib/api";
import { useAuthStore } from "@/store/auth-store";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { GroupListItem, PaginatedResponse } from "@/types/dashboard";
import type { GroupRecord } from "@/types/group";
import type { TemplateRecord } from "@/types/template";

const TEMPLATE_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_TEMPLATE_PAGE: PaginatedResponse<TemplateRecord> = {
  items: [],
  page: 0,
  size: TEMPLATE_PAGE_SIZE,
  totalItems: 0,
};

const TemplatesPage = () => {
  const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);

  const [templatePage, setTemplatePage] =
    useState<PaginatedResponse<TemplateRecord>>(EMPTY_TEMPLATE_PAGE);
  const [groups, setGroups] = useState<GroupListItem[]>([]);
  const [serverGroups, setServerGroups] = useState<GroupRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingGroups, setIsLoadingGroups] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [groupFilter, setGroupFilter] = useState("");
  const [isUploadModalOpen, setIsUploadModalOpen] = useState(false);
  const [uploadGroup, setUploadGroup] = useState("");
  const [uploadVersion, setUploadVersion] = useState("");
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [activeTemplateActionKey, setActiveTemplateActionKey] = useState<string | null>(
    null
  );
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const getValidAccessToken = useCallback(async () => {
    const nextSession = await refreshIfNeeded();

    if (!nextSession) {
      throw new Error("Your session expired. Please sign in again.");
    }

    return nextSession.accessToken;
  }, [refreshIfNeeded]);

  const loadTemplatePage = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setIsLoading(true);
    }

    try {
      const accessToken = await getValidAccessToken();
      const nextPage = await listTemplates(accessToken, {
        group: groupFilter || undefined,
        page: currentPage,
        size: TEMPLATE_PAGE_SIZE,
      });

      setTemplatePage(nextPage);
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to load templates."
      );
    } finally {
      setIsLoading(false);
    }
  }, [currentPage, getValidAccessToken, groupFilter]);

  useEffect(() => {
    let active = true;

    const loadAvailableGroups = async () => {
      try {
        const accessToken = await getValidAccessToken();
        const [nextGroups, nextServerGroups] = await Promise.all([
          listGroups(accessToken),
          listAllServerGroups(accessToken),
        ]);

        if (!active) {
          return;
        }

        setGroups(nextGroups);
        setServerGroups(nextServerGroups);
      } catch (error) {
        if (!active) {
          return;
        }

        setErrorMessage(
          error instanceof Error ? error.message : "Unable to load groups."
        );
      } finally {
        if (active) {
          setIsLoadingGroups(false);
        }
      }
    };

    void loadAvailableGroups();

    return () => {
      active = false;
    };
  }, [getValidAccessToken]);

  useEffect(() => {
    let active = true;

    const runLoad = async (showLoading = true) => {
      if (!active) {
        return;
      }

      await loadTemplatePage(showLoading);
    };

    void runLoad(true);

    const intervalId = window.setInterval(() => {
      void runLoad(false);
    }, REFRESH_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [loadTemplatePage]);

  useEffect(() => {
    if (!successMessage) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setSuccessMessage(null);
    }, 3000);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [successMessage]);

  const handleGroupFilterChange = (nextValue: string) => {
    setCurrentPage(0);
    setGroupFilter(nextValue);
  };

  const openUploadModal = () => {
    setUploadGroup("");
    setUploadVersion("");
    setUploadFile(null);
    setIsUploading(false);
    setIsUploadModalOpen(true);
  };

  const closeUploadModal = () => {
    if (isUploading) {
      return;
    }

    setIsUploadModalOpen(false);
    setUploadGroup("");
    setUploadVersion("");
    setUploadFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleUploadTemplate = async () => {
    if (!uploadGroup.trim()) {
      setErrorMessage("Choose a target group first.");
      return;
    }

    if (!uploadVersion.trim()) {
      setErrorMessage("Enter a template version.");
      return;
    }

    if (!uploadFile) {
      setErrorMessage("Choose a template archive first.");
      return;
    }

    setIsUploading(true);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      await uploadTemplate(
        accessToken,
        uploadGroup.trim(),
        uploadVersion.trim(),
        uploadFile
      );

      setSuccessMessage(
        `Uploaded ${uploadGroup.trim()} / ${uploadVersion.trim()}.`
      );
      setIsUploadModalOpen(false);
      setUploadGroup("");
      setUploadVersion("");
      setUploadFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      await loadTemplatePage(false);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to upload template."
      );
    } finally {
      setIsUploading(false);
    }
  };

  const handleDownloadTemplate = async (template: TemplateRecord) => {
    const actionKey = `${template.group}:${template.version}:download`;
    setActiveTemplateActionKey(actionKey);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      await downloadTemplate(accessToken, template.group, template.version);
      setSuccessMessage(`Downloaded ${template.group} / ${template.version}.`);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to download template."
      );
    } finally {
      setActiveTemplateActionKey(null);
    }
  };

  const handleDeleteTemplate = async (template: TemplateRecord) => {
    const actionKey = `${template.group}:${template.version}:delete`;
    setActiveTemplateActionKey(actionKey);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      await deleteTemplate(accessToken, template.group, template.version);
      setSuccessMessage(`Deleted ${template.group} / ${template.version}.`);

      if (templatePage.items.length === 1 && currentPage > 0) {
        setCurrentPage((value) => Math.max(0, value - 1));
      } else {
        await loadTemplatePage(false);
      }
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to delete template."
      );
    } finally {
      setActiveTemplateActionKey(null);
    }
  };

  const totalPages = getPaginatedTotalPages(templatePage);
  const usedTemplateKeys = new Set(
    serverGroups.map((group) => `${group.templatePath}::${group.templateVersion}`)
  );

  return (
    <div className="space-y-8">
      <AppToasts
        items={[
          ...(errorMessage
            ? [
                {
                  id: "templates-error",
                  message: errorMessage,
                  onDismiss: () => setErrorMessage(null),
                  tone: "error" as const,
                },
              ]
            : []),
          ...(successMessage
            ? [
                {
                  id: "templates-success",
                  message: successMessage,
                  onDismiss: () => setSuccessMessage(null),
                  tone: "success" as const,
                },
              ]
            : []),
        ]}
      />

      <motion.section
        initial={{ y: 12, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut" }}
        className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
      >
        <div>
          <h2 className="flex items-center gap-2 text-lg font-bold text-white">
            <FiFileText className="h-5 w-5 text-primary" />
            Templates
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Review uploaded template archives and publish new versions for server
            groups.
          </p>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="flex items-center gap-2">
            <div className="min-w-48">
              <AppSelect
                value={groupFilter}
                onChangeValue={handleGroupFilterChange}
                disabled={isLoadingGroups}
              >
                <option value="">All groups</option>
                {groups.map((group) => (
                  <option key={group.id} value={group.id}>
                    {group.id}
                  </option>
                ))}
              </AppSelect>
            </div>

            <button
              type="button"
              onClick={openUploadModal}
              className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-semibold text-slate-950"
            >
              <FiUpload className="h-4 w-4" />
              Upload Template
            </button>
          </div>
        </div>
      </motion.section>

      <motion.section
        initial={{ y: 16, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
        className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
      >
        <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Template Archives
              </h3>
            </div>
            <div className="flex items-center justify-between gap-3 sm:justify-end">
              <span className="text-xs text-slate-500">
                {templatePage.totalItems} template
                {templatePage.totalItems === 1 ? "" : "s"}
              </span>
              <TableRefreshButton
                onClick={() => {
                  void loadTemplatePage(false);
                }}
                isRefreshing={isLoading}
                label="Refresh template table"
              />
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-left">
            <thead>
              <tr className="border-b border-slate-800 bg-slate-800/30">
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Group
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Version
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Path
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Artifact
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                  Used
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase text-slate-500">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {isLoading ? (
                <tr>
                  <td
                    colSpan={6}
                    className="px-6 py-10 text-center text-sm text-slate-400"
                  >
                    Loading templates...
                  </td>
                </tr>
              ) : templatePage.items.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className="px-6 py-10 text-center text-sm text-slate-400"
                  >
                    No templates matched this filter.
                  </td>
                </tr>
              ) : (
                templatePage.items.map((template) => (
                  <tr key={`${template.group}:${template.version}:${template.path}`}>
                    <td className="px-6 py-4">
                      <span className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                        <FiFolder className="h-3.5 w-3.5" />
                        {template.group}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <span className="font-mono text-sm font-semibold text-slate-100">
                        {template.version}
                      </span>
                    </td>
                    <td className="px-6 py-4">
                      <p className="break-all font-mono text-xs text-slate-400">
                        {template.path}
                      </p>
                    </td>
                    <td className="px-6 py-4 text-sm text-slate-300">
                      template.tar.gz
                    </td>
                    <td className="px-6 py-4">
                      {usedTemplateKeys.has(`${template.group}::${template.version}`) ? (
                        <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-semibold text-emerald-300">
                          Yes
                        </span>
                      ) : (
                        <span className="inline-flex items-center rounded-full bg-slate-800 px-2.5 py-1 text-xs font-semibold text-slate-300">
                          No
                        </span>
                      )}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          type="button"
                          disabled={activeTemplateActionKey !== null}
                          onClick={() => void handleDownloadTemplate(template)}
                          className="button-hover-lift button-shadow-primary inline-flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary disabled:cursor-not-allowed disabled:opacity-60"
                          aria-label={`Download ${template.group} ${template.version}`}
                          title="Download template"
                        >
                          <FiDownload className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          disabled={activeTemplateActionKey !== null}
                          onClick={() => void handleDeleteTemplate(template)}
                          className="button-hover-lift button-shadow-danger inline-flex h-8 w-8 items-center justify-center rounded-lg bg-red-500/10 text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                          aria-label={`Delete ${template.group} ${template.version}`}
                          title="Delete template"
                        >
                          <FiTrash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className="flex flex-col gap-3 border-t border-slate-800 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-slate-400">
            Page {templatePage.page + 1} of {totalPages}
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={currentPage === 0 || isLoading}
              onClick={() => setCurrentPage((value) => Math.max(0, value - 1))}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <FiChevronLeft className="h-4 w-4" />
              Previous
            </button>
            <button
              type="button"
              disabled={!getPaginatedHasNext(templatePage) || isLoading}
              onClick={() => setCurrentPage((value) => value + 1)}
              className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
            >
              Next
              <FiChevronRight className="h-4 w-4" />
            </button>
          </div>
        </div>
      </motion.section>

      {isUploadModalOpen && (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
          <motion.div
            initial={{ y: 12, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
            className="w-full max-w-2xl rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
              <div>
                <h3 className="text-base font-semibold text-white">Upload Template</h3>
                <p className="text-sm text-slate-400">
                  Publish a new `template.tar.gz` archive for a server group.
                </p>
              </div>
              <button
                type="button"
                onClick={closeUploadModal}
                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                aria-label="Close"
              >
                <FiX className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-5 p-6">
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <AppCreatableSelect
                  label="Target Group"
                  value={uploadGroup}
                  onChangeValue={setUploadGroup}
                  options={groups.map((group) => group.id)}
                  disabled={isUploading}
                  placeholder="Choose or type group id"
                  emptyLabel="No groups available"
                  hint="Pick an existing group or type a new group name."
                />

                <label className="app-field-stack">
                  <span className="app-field-label">Version</span>
                  <input
                    type="text"
                    value={uploadVersion}
                    onChange={(event) => setUploadVersion(event.target.value)}
                    disabled={isUploading}
                    className="app-input-field block w-full px-3"
                    placeholder="1.0.0"
                  />
                </label>
              </div>

              <div className="app-field-stack">
                <span className="app-field-label">Template Archive</span>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".gz,.tar.gz,application/gzip,application/x-gzip"
                  className="hidden"
                  onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
                />
                <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center justify-center gap-2 rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
                  >
                    <FiUpload className="h-4 w-4" />
                    Choose File
                  </button>
                  <div className="min-h-12 flex-1 rounded-lg border border-slate-800 bg-slate-950/40 px-4 py-3 text-sm text-slate-400">
                    {uploadFile
                      ? `${uploadFile.name} (${Math.max(
                          1,
                          Math.round(uploadFile.size / 1024)
                        )} KB)`
                      : "No file selected"}
                  </div>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
              <button
                type="button"
                onClick={closeUploadModal}
                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isUploading}
                onClick={() => void handleUploadTemplate()}
                className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isUploading ? "Uploading..." : "Upload Template"}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </div>
  );
};

export default TemplatesPage;
