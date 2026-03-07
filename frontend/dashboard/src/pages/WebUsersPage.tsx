import { useCallback, useEffect, useState } from "react";
import { motion } from "motion/react";
import {
  FiChevronLeft,
  FiChevronRight,
  FiEdit2,
  FiLink,
  FiPlus,
  FiShield,
  FiTrash2,
  FiUserPlus,
  FiX,
} from "react-icons/fi";

import AppSelect from "@/components/AppSelect";
import AppToasts from "@/components/AppToasts";
import TableRefreshButton from "@/components/TableRefreshButton";
import {
  createWebUser,
  deleteWebUser,
  listWebUsers,
  unlinkWebUserAccount,
  updateWebUser,
} from "@/lib/api";
import { hasAdminAccess, normalizeRole } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { PaginatedResponse } from "@/types/dashboard";
import {
  WEB_USER_ROLES,
  type CreateWebUserPayload,
  type UpdateWebUserPayload,
  type WebUserRecord,
  type WebUserRole,
} from "@/types/web-user";

const WEB_USER_PAGE_SIZE = 10;
const REFRESH_INTERVAL_MS = 10_000;

const EMPTY_WEB_USER_PAGE: PaginatedResponse<WebUserRecord> = {
  items: [],
  page: 0,
  size: WEB_USER_PAGE_SIZE,
  totalItems: 0,
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const createEmptyWebUserValues = (): CreateWebUserPayload => ({
  email: "",
  password: "",
  role: "DEVELOPER",
});

interface WebUserEditValues {
  email: string;
  username: string;
  password: string;
  role: WebUserRole;
}

const createEditValuesFromUser = (user: WebUserRecord): WebUserEditValues => ({
  email: user.email,
  username: user.username,
  password: "",
  role: user.role,
});

const getRoleBadgeClass = (role: string) => {
  const normalizedRole = normalizeRole(role);

  if (normalizedRole === "admin") {
    return "bg-red-500/10 text-red-300";
  }

  if (normalizedRole === "service") {
    return "bg-amber-500/10 text-amber-300";
  }

  return "bg-primary/10 text-primary";
};

const WebUsersPage = () => {
  const session = useAuthStore((state) => state.session);
  const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
  const updateUser = useAuthStore((state) => state.updateUser);
  const normalizedUserRole = normalizeRole(session?.user.role);
  const canManageWebUsers = hasAdminAccess(normalizedUserRole);
  const normalizedCurrentUserEmail = session?.user.email.trim().toLowerCase() ?? "";

  const [webUserPage, setWebUserPage] =
    useState<PaginatedResponse<WebUserRecord>>(EMPTY_WEB_USER_PAGE);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(0);
  const [query, setQuery] = useState("");
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [createValues, setCreateValues] = useState<CreateWebUserPayload>(
    createEmptyWebUserValues()
  );
  const [editTargetUser, setEditTargetUser] = useState<WebUserRecord | null>(null);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isUpdating, setIsUpdating] = useState(false);
  const [editValues, setEditValues] = useState<WebUserEditValues>({
    email: "",
    username: "",
    password: "",
    role: "DEVELOPER",
  });
  const [deleteTargetUser, setDeleteTargetUser] = useState<WebUserRecord | null>(null);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [activeUnlinkEmail, setActiveUnlinkEmail] = useState<string | null>(null);

  const getValidAccessToken = useCallback(async () => {
    const nextSession = await refreshIfNeeded();

    if (!nextSession) {
      throw new Error("Your session expired. Please sign in again.");
    }

    return nextSession.accessToken;
  }, [refreshIfNeeded]);

  const loadWebUsers = useCallback(async (showLoading = true) => {
    if (!canManageWebUsers) {
      setWebUserPage(EMPTY_WEB_USER_PAGE);
      setIsLoading(false);
      return;
    }

    if (showLoading) {
      setIsLoading(true);
    }

    try {
      const accessToken = await getValidAccessToken();
      const nextPage = await listWebUsers(accessToken, {
        query: query.trim() || undefined,
        page: currentPage,
        size: WEB_USER_PAGE_SIZE,
      });

      setWebUserPage(nextPage);
      setErrorMessage(null);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to load web users.");
    } finally {
      setIsLoading(false);
    }
  }, [canManageWebUsers, currentPage, getValidAccessToken, query]);

  useEffect(() => {
    let active = true;

    const runLoad = async (showLoading = true) => {
      if (!active) {
        return;
      }

      await loadWebUsers(showLoading);
    };

    void runLoad(true);

    const intervalId = window.setInterval(() => {
      void runLoad(false);
    }, REFRESH_INTERVAL_MS);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, [loadWebUsers]);

  useEffect(() => {
    setCurrentPage(0);
  }, [query]);

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

  const closeCreateModal = () => {
    if (isCreating) {
      return;
    }

    setIsCreateModalOpen(false);
    setCreateValues(createEmptyWebUserValues());
  };

  const openEditModal = (user: WebUserRecord) => {
    setEditTargetUser(user);
    setEditValues(createEditValuesFromUser(user));
    setIsEditModalOpen(true);
  };

  const closeEditModal = () => {
    setIsEditModalOpen(false);
    setEditTargetUser(null);
    setEditValues({
      email: "",
      username: "",
      password: "",
      role: "DEVELOPER",
    });
  };

  const openDeleteModal = (user: WebUserRecord) => {
    setDeleteTargetUser(user);
    setIsDeleteModalOpen(true);
  };

  const closeDeleteModal = () => {
    setIsDeleteModalOpen(false);
    setDeleteTargetUser(null);
  };

  const handleCreateWebUser = async () => {
    const normalizedEmail = createValues.email.trim().toLowerCase();

    if (!normalizedEmail) {
      setErrorMessage("Email is required.");
      return;
    }

    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      setErrorMessage("Enter a valid email address.");
      return;
    }

    if (!createValues.password.trim()) {
      setErrorMessage("Password is required.");
      return;
    }

    setIsCreating(true);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      const createdUser = await createWebUser(accessToken, {
        email: normalizedEmail,
        password: createValues.password,
        role: createValues.role,
      });

      setSuccessMessage(`Created web user ${createdUser.email}.`);
      setIsCreateModalOpen(false);
      setCreateValues(createEmptyWebUserValues());

      if (currentPage !== 0) {
        setCurrentPage(0);
      } else {
        await loadWebUsers(false);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to create web user.");
    } finally {
      setIsCreating(false);
    }
  };

  const handleUpdateWebUser = async () => {
    if (!editTargetUser) {
      return;
    }

    const normalizedEmail = editValues.email.trim().toLowerCase();
    const normalizedUsername = editValues.username.trim();
    const normalizedPassword = editValues.password.trim();
    const payload: UpdateWebUserPayload = {};

    if (!normalizedEmail) {
      setErrorMessage("Email is required.");
      return;
    }

    if (!EMAIL_PATTERN.test(normalizedEmail)) {
      setErrorMessage("Enter a valid email address.");
      return;
    }

    if (!normalizedUsername) {
      setErrorMessage("Username is required.");
      return;
    }

    if (normalizedEmail !== editTargetUser.email) {
      payload.email = normalizedEmail;
    }

    if (normalizedUsername !== editTargetUser.username) {
      payload.username = normalizedUsername;
    }

    if (normalizedPassword) {
      payload.password = editValues.password;
    }

    if (editValues.role !== editTargetUser.role) {
      payload.role = editValues.role;
    }

    if (Object.keys(payload).length === 0) {
      setErrorMessage("No changes to update.");
      return;
    }

    setIsUpdating(true);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      const updatedUser = await updateWebUser(accessToken, editTargetUser.email, payload);

      setSuccessMessage(`Updated web user ${updatedUser.email}.`);
      closeEditModal();
      await loadWebUsers(false);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to update web user.");
    } finally {
      setIsUpdating(false);
    }
  };

  const handleDeleteWebUser = async () => {
    if (!deleteTargetUser) {
      return;
    }

    setIsDeleting(true);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      await deleteWebUser(accessToken, deleteTargetUser.email);

      setSuccessMessage(`Deleted web user ${deleteTargetUser.email}.`);
      closeDeleteModal();

      if (webUserPage.items.length === 1 && currentPage > 0) {
        setCurrentPage((value) => Math.max(0, value - 1));
      } else {
        await loadWebUsers(false);
      }
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : "Unable to delete web user.");
    } finally {
      setIsDeleting(false);
    }
  };

  const handleUnlinkWebUser = async (user: WebUserRecord) => {
    if (!user.linkedPlayerUuid) {
      return;
    }

    const normalizedTargetEmail = user.email.trim().toLowerCase();

    setActiveUnlinkEmail(normalizedTargetEmail);
    setErrorMessage(null);

    try {
      const accessToken = await getValidAccessToken();
      const updatedUser = await unlinkWebUserAccount(accessToken, user.email);

      if (normalizedCurrentUserEmail === normalizedTargetEmail) {
        updateUser(updatedUser);
      }

      setSuccessMessage(`Unlinked Minecraft account for ${updatedUser.email}.`);
      await loadWebUsers(false);
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : "Unable to unlink Minecraft account."
      );
    } finally {
      setActiveUnlinkEmail(null);
    }
  };

  const totalPages = getPaginatedTotalPages(webUserPage);

  return (
    <div className="space-y-8">
      <AppToasts
        items={[
          ...(errorMessage
            ? [
                {
                  id: "web-users-error",
                  message: errorMessage,
                  onDismiss: () => setErrorMessage(null),
                  tone: "error" as const,
                },
              ]
            : []),
          ...(successMessage
            ? [
                {
                  id: "web-users-success",
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
            <FiUserPlus className="h-5 w-5 text-primary" />
            Web Users
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Provision dashboard users and assign account roles.
          </p>
        </div>
        {canManageWebUsers ? (
          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="w-full min-w-72">
              <input
                type="text"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search email, username, role..."
                className="app-input-field block w-full px-3"
              />
            </div>
            <button
              type="button"
              onClick={() => setIsCreateModalOpen(true)}
              className="app-button-field button-hover-lift button-shadow-primary inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold whitespace-nowrap text-slate-950"
            >
              <FiPlus className="h-4 w-4" />
              Create User
            </button>
          </div>
        ) : null}
      </motion.section>

      {!canManageWebUsers ? (
        <motion.section
          initial={{ y: 16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
          className="rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-sm"
        >
          <div className="flex items-start gap-3">
            <div className="rounded-lg bg-amber-500/10 p-2 text-amber-300">
              <FiShield className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white">Restricted</h3>
              <p className="mt-1 text-sm text-slate-400">
                Only admin and service accounts can manage web users.
              </p>
            </div>
          </div>
        </motion.section>
      ) : (
        <motion.section
          initial={{ y: 16, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ duration: 0.35, ease: "easeOut", delay: 0.05 }}
          className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
        >
          <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                Accounts
              </h3>
              <div className="flex items-center justify-between gap-3 sm:justify-end">
                <span className="text-xs text-slate-500">
                  {webUserPage.totalItems} user{webUserPage.totalItems === 1 ? "" : "s"}
                </span>
                <TableRefreshButton
                  onClick={() => {
                    void loadWebUsers(false);
                  }}
                  isRefreshing={isLoading}
                  label="Refresh web user table"
                />
              </div>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left">
              <thead>
                <tr className="border-b border-slate-800 bg-slate-800/30">
                  <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                    Email
                  </th>
                  <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                    Username
                  </th>
                  <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                    Role
                  </th>
                  <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                    Linked Player
                  </th>
                  <th className="px-6 py-4 text-right text-xs font-semibold uppercase text-slate-500">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {isLoading ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-10 text-center text-sm text-slate-400">
                      Loading web users...
                    </td>
                  </tr>
                ) : webUserPage.items.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-6 py-10 text-center text-sm text-slate-400">
                      No web users matched your search.
                    </td>
                  </tr>
                ) : (
                  webUserPage.items.map((user) => (
                    <tr key={user.id}>
                      <td className="px-6 py-4">
                        <p className="text-sm font-semibold text-white">{user.email}</p>
                      </td>
                      <td className="px-6 py-4 text-sm text-slate-300">{user.username}</td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-semibold ${getRoleBadgeClass(
                            user.role
                          )}`}
                        >
                          {user.role}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <p className="font-mono text-xs text-slate-400">
                          {user.linkedPlayerUuid || "--"}
                        </p>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            type="button"
                            onClick={() => void handleUnlinkWebUser(user)}
                            disabled={
                              isUpdating ||
                              isDeleting ||
                              activeUnlinkEmail !== null ||
                              !user.linkedPlayerUuid
                            }
                            className="button-hover-lift button-shadow-warning inline-flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/10 text-amber-300 disabled:cursor-not-allowed disabled:opacity-60"
                            aria-label={`Unlink ${user.email}`}
                            title={`Unlink ${user.email}`}
                          >
                            <FiLink className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => openEditModal(user)}
                            disabled={isUpdating || isDeleting}
                            className="button-hover-lift button-shadow-primary inline-flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary disabled:cursor-not-allowed disabled:opacity-60"
                            aria-label={`Edit ${user.email}`}
                            title={`Edit ${user.email}`}
                          >
                            <FiEdit2 className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => openDeleteModal(user)}
                            disabled={isUpdating || isDeleting}
                            className="button-hover-lift button-shadow-danger inline-flex h-8 w-8 items-center justify-center rounded-lg bg-red-500/10 text-red-300 disabled:cursor-not-allowed disabled:opacity-60"
                            aria-label={`Delete ${user.email}`}
                            title={`Delete ${user.email}`}
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
              Page {webUserPage.page + 1} of {totalPages}
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
                disabled={!getPaginatedHasNext(webUserPage) || isLoading}
                onClick={() => setCurrentPage((value) => value + 1)}
                className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
              >
                Next
                <FiChevronRight className="h-4 w-4" />
              </button>
            </div>
          </div>
        </motion.section>
      )}

      {isCreateModalOpen ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
          <motion.div
            initial={{ y: 12, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
            className="w-full max-w-xl rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
              <div>
                <h3 className="text-base font-semibold text-white">Create User</h3>
                <p className="text-sm text-slate-400">
                  Create a dashboard account and assign a role.
                </p>
              </div>
              <button
                type="button"
                onClick={closeCreateModal}
                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary"
                aria-label="Close"
              >
                <FiX className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-5 p-6">
              <label className="app-field-stack">
                <span className="app-field-label">Email</span>
                <input
                  type="email"
                  value={createValues.email}
                  onChange={(event) =>
                    setCreateValues((currentValue) => ({
                      ...currentValue,
                      email: event.target.value,
                    }))
                  }
                  disabled={isCreating}
                  className="app-input-field block w-full px-3"
                  placeholder="user@ogcloud.local"
                />
              </label>

              <label className="app-field-stack">
                <span className="app-field-label">Password</span>
                <input
                  type="password"
                  value={createValues.password}
                  onChange={(event) =>
                    setCreateValues((currentValue) => ({
                      ...currentValue,
                      password: event.target.value,
                    }))
                  }
                  disabled={isCreating}
                  className="app-input-field block w-full px-3"
                  placeholder="Set an initial password"
                />
              </label>

              <AppSelect
                label="Role"
                labelHint="Controls which dashboard actions this account is allowed to perform."
                value={createValues.role}
                onChangeValue={(nextRole) =>
                  setCreateValues((currentValue) => ({
                    ...currentValue,
                    role: nextRole as CreateWebUserPayload["role"],
                  }))
                }
                disabled={isCreating}
              >
                {WEB_USER_ROLES.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </AppSelect>
            </div>

            <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
              <button
                type="button"
                onClick={closeCreateModal}
                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isCreating}
                onClick={() => void handleCreateWebUser()}
                className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isCreating ? "Creating..." : "Create User"}
              </button>
            </div>
          </motion.div>
        </div>
      ) : null}

      {isEditModalOpen && editTargetUser ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
          <motion.div
            initial={{ y: 12, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
            className="w-full max-w-xl rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
          >
            <div className="flex items-center justify-between border-b border-slate-800 px-6 py-4">
              <div>
                <h3 className="text-base font-semibold text-white">Update User</h3>
                <p className="text-sm text-slate-400">
                  Modify account details for {editTargetUser.email}.
                </p>
              </div>
              <button
                type="button"
                onClick={closeEditModal}
                disabled={isUpdating}
                className="rounded-lg p-1.5 text-slate-400 transition-colors duration-150 hover:bg-slate-800 hover:text-primary disabled:cursor-not-allowed disabled:opacity-60"
                aria-label="Close"
              >
                <FiX className="h-4 w-4" />
              </button>
            </div>

            <div className="space-y-5 p-6">
              <label className="app-field-stack">
                <span className="app-field-label">Email</span>
                <input
                  type="email"
                  value={editValues.email}
                  onChange={(event) =>
                    setEditValues((currentValue) => ({
                      ...currentValue,
                      email: event.target.value,
                    }))
                  }
                  disabled={isUpdating}
                  className="app-input-field block w-full px-3"
                />
              </label>

              <label className="app-field-stack">
                <span className="app-field-label">Username</span>
                <input
                  type="text"
                  value={editValues.username}
                  onChange={(event) =>
                    setEditValues((currentValue) => ({
                      ...currentValue,
                      username: event.target.value,
                    }))
                  }
                  disabled={isUpdating}
                  className="app-input-field block w-full px-3"
                />
              </label>

              <label className="app-field-stack">
                <span className="app-field-label">New Password (optional)</span>
                <input
                  type="password"
                  value={editValues.password}
                  onChange={(event) =>
                    setEditValues((currentValue) => ({
                      ...currentValue,
                      password: event.target.value,
                    }))
                  }
                  disabled={isUpdating}
                  className="app-input-field block w-full px-3"
                  placeholder="Leave blank to keep current password"
                />
              </label>

              <AppSelect
                label="Role"
                labelHint="Controls which dashboard actions this account is allowed to perform."
                value={editValues.role}
                onChangeValue={(nextRole) =>
                  setEditValues((currentValue) => ({
                    ...currentValue,
                    role: nextRole as WebUserRole,
                  }))
                }
                disabled={isUpdating}
              >
                {WEB_USER_ROLES.map((role) => (
                  <option key={role} value={role}>
                    {role}
                  </option>
                ))}
              </AppSelect>
            </div>

            <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
              <button
                type="button"
                onClick={closeEditModal}
                disabled={isUpdating}
                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isUpdating}
                onClick={() => void handleUpdateWebUser()}
                className="app-button-field button-hover-lift button-shadow-primary rounded-lg bg-primary px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isUpdating ? "Updating..." : "Update User"}
              </button>
            </div>
          </motion.div>
        </div>
      ) : null}

      {isDeleteModalOpen && deleteTargetUser ? (
        <div className="fixed inset-0 z-40 flex items-center justify-center bg-slate-950/70 p-4 backdrop-blur-sm">
          <motion.div
            initial={{ y: 12, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.25, ease: "easeOut" }}
            className="w-full max-w-lg rounded-xl border border-slate-800 bg-slate-900 shadow-2xl"
          >
            <div className="border-b border-slate-800 px-6 py-4">
              <h3 className="text-base font-semibold text-white">Delete User</h3>
              <p className="mt-1 text-sm text-slate-400">
                Are you sure you want to delete {deleteTargetUser.email}?
              </p>
            </div>

            <div className="px-6 py-5 text-sm text-slate-300">
              This action removes the account and revokes all active sessions.
            </div>

            <div className="flex justify-end gap-3 border-t border-slate-800 px-6 py-4">
              <button
                type="button"
                onClick={closeDeleteModal}
                disabled={isDeleting}
                className="app-button-field button-hover-lift button-shadow-neutral rounded-lg border border-slate-700 px-4 py-2.5 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => void handleDeleteWebUser()}
                disabled={isDeleting}
                className="app-button-field button-hover-lift button-shadow-danger rounded-lg bg-red-500 px-4 py-2.5 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isDeleting ? "Deleting..." : "Delete User"}
              </button>
            </div>
          </motion.div>
        </div>
      ) : null}
    </div>
  );
};

export default WebUsersPage;
