import {useCallback, useEffect, useState} from "react";
import {motion} from "motion/react";
import {FiBell, FiChevronLeft, FiChevronRight, FiShield,} from "react-icons/fi";

import AppToasts from "@/components/AppToasts";
import TableRefreshButton from "@/components/TableRefreshButton";
import {listApiAuditLogs} from "@/lib/api";
import {hasAdminAccess, normalizeRole} from "@/lib/roles";
import {formatDateTime} from "@/lib/server-display";
import {useAuthStore} from "@/store/auth-store";
import type {ApiAuditLogRecord} from "@/types/audit";
import type {PaginatedResponse} from "@/types/dashboard";
import {getPaginatedHasNext, getPaginatedTotalPages} from "@/types/dashboard";

const AUDIT_PAGE_SIZE = 10;

const EMPTY_AUDIT_PAGE: PaginatedResponse<ApiAuditLogRecord> = {
    items: [],
    page: 0,
    size: AUDIT_PAGE_SIZE,
    totalItems: 0,
};

const InboxPage = () => {
    const session = useAuthStore((state) => state.session);
    const refreshIfNeeded = useAuthStore((state) => state.refreshIfNeeded);
    const normalizedUserRole = normalizeRole(session?.user.role);
    const isAdmin = hasAdminAccess(normalizedUserRole);

    const [auditPage, setAuditPage] =
        useState<PaginatedResponse<ApiAuditLogRecord>>(EMPTY_AUDIT_PAGE);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(0);
    const [query, setQuery] = useState("");

    const getValidAccessToken = useCallback(async () => {
        const nextSession = await refreshIfNeeded();

        if (!nextSession) {
            throw new Error("Your session expired. Please sign in again.");
        }

        return nextSession.accessToken;
    }, [refreshIfNeeded]);

    const loadAuditLogs = useCallback(async (showLoading = true) => {
        if (!isAdmin) {
            setAuditPage(EMPTY_AUDIT_PAGE);
            setIsLoading(false);
            return;
        }

        if (showLoading) {
            setIsLoading(true);
        }

        try {
            const accessToken = await getValidAccessToken();
            const nextPage = await listApiAuditLogs(accessToken, {
                query: query.trim() || undefined,
                page: currentPage,
                size: AUDIT_PAGE_SIZE,
            });

            setAuditPage(nextPage);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error ? error.message : "Unable to load API audit logs."
            );
        } finally {
            setIsLoading(false);
        }
    }, [currentPage, getValidAccessToken, isAdmin, query]);

    useEffect(() => {
        let active = true;

        const runLoad = async () => {
            if (!active) {
                return;
            }

            await loadAuditLogs(true);
        };

        void runLoad();

        return () => {
            active = false;
        };
    }, [loadAuditLogs]);

    useEffect(() => {
        setCurrentPage(0);
    }, [query]);

    const totalPages = getPaginatedTotalPages(auditPage);

    return (
        <div className="space-y-8">
            <AppToasts
                items={
                    errorMessage
                        ? [
                            {
                                id: "audit-inbox-error",
                                message: errorMessage,
                                onDismiss: () => setErrorMessage(null),
                                tone: "error" as const,
                            },
                        ]
                        : []
                }
            />

            <motion.section
                initial={{y: 12, opacity: 0}}
                animate={{y: 0, opacity: 1}}
                transition={{duration: 0.35, ease: "easeOut"}}
                className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
            >
                <div>
                    <h2 className="flex items-center gap-2 text-lg font-bold text-white">
                        <FiBell className="h-5 w-5 text-primary"/>
                        Inbox
                    </h2>
                    <p className="mt-1 text-sm text-slate-400">
                        Review API audit activity recorded by the control plane.
                    </p>
                </div>
                {isAdmin ? (
                    <div className="w-full max-w-sm">
                        <input
                            type="text"
                            value={query}
                            onChange={(event) => setQuery(event.target.value)}
                            placeholder="Search action, actor, or summary..."
                            className="app-input-field block w-full px-3"
                        />
                    </div>
                ) : null}
            </motion.section>

            {!isAdmin ? (
                <motion.section
                    initial={{y: 16, opacity: 0}}
                    animate={{y: 0, opacity: 1}}
                    transition={{duration: 0.35, ease: "easeOut", delay: 0.05}}
                    className="rounded-xl border border-slate-800 bg-slate-900 p-6 shadow-sm"
                >
                    <div className="flex items-start gap-3">
                        <div className="rounded-lg bg-amber-500/10 p-2 text-amber-300">
                            <FiShield className="h-5 w-5"/>
                        </div>
                        <div>
                            <h3 className="text-sm font-semibold text-white">Restricted</h3>
                            <p className="mt-1 text-sm text-slate-400">
                                API audit logs are only available to administrator-level roles.
                            </p>
                        </div>
                    </div>
                </motion.section>
            ) : (
                <motion.section
                    initial={{y: 16, opacity: 0}}
                    animate={{y: 0, opacity: 1}}
                    transition={{duration: 0.35, ease: "easeOut", delay: 0.05}}
                    className="overflow-hidden rounded-xl border border-slate-800 bg-slate-900 shadow-sm"
                >
                    <div className="rounded-t-xl border-b border-slate-800 bg-slate-800/50 px-6 py-4">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <h3 className="text-sm font-semibold uppercase tracking-wide text-slate-400">
                                API Audit Log
                            </h3>
                            <div className="flex items-center justify-between gap-3 sm:justify-end">
                <span className="text-xs text-slate-500">
                  {auditPage.totalItems} log entr{auditPage.totalItems === 1 ? "y" : "ies"}
                </span>
                                <TableRefreshButton
                                    onClick={() => {
                                        void loadAuditLogs(false);
                                    }}
                                    isRefreshing={isLoading}
                                    label="Refresh audit log"
                                />
                            </div>
                        </div>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-left">
                            <thead>
                            <tr className="border-b border-slate-800 bg-slate-800/30">
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Action
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Target
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Summary
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Actor
                                </th>
                                <th className="px-6 py-4 text-xs font-semibold uppercase text-slate-500">
                                    Time
                                </th>
                            </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-800">
                            {isLoading ? (
                                <tr>
                                    <td
                                        colSpan={5}
                                        className="px-6 py-10 text-center text-sm text-slate-400"
                                    >
                                        Loading audit log entries...
                                    </td>
                                </tr>
                            ) : auditPage.items.length === 0 ? (
                                <tr>
                                    <td
                                        colSpan={5}
                                        className="px-6 py-10 text-center text-sm text-slate-400"
                                    >
                                        No audit log entries were found.
                                    </td>
                                </tr>
                            ) : (
                                auditPage.items.map((entry) => (
                                    <tr key={entry.id ?? `${entry.action}:${entry.timestamp}:${entry.targetId}`}>
                                        <td className="px-6 py-4">
                        <span
                            className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">
                          {entry.action}
                        </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div>
                                                <p className="text-sm font-semibold text-white">{entry.targetId}</p>
                                                <p className="text-xs text-slate-500">{entry.targetType}</p>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div>
                                                <p className="text-sm text-slate-200">{entry.summary}</p>
                                                {Object.keys(entry.metadata).length > 0 ? (
                                                    <p className="mt-1 truncate text-xs text-slate-500">
                                                        {Object.entries(entry.metadata)
                                                            .slice(0, 2)
                                                            .map(([key, value]) => `${key}: ${value}`)
                                                            .join(" | ")}
                                                    </p>
                                                ) : null}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4">
                                            <div>
                                                <p className="text-sm text-slate-200">
                                                    {entry.actorEmail || entry.actorUserId || "System"}
                                                </p>
                                                <p className="text-xs text-slate-500">
                                                    {entry.actorUserId && entry.actorEmail ? entry.actorUserId : " "}
                                                </p>
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-slate-400">
                                            {formatDateTime(entry.timestamp)}
                                        </td>
                                    </tr>
                                ))
                            )}
                            </tbody>
                        </table>
                    </div>

                    <div
                        className="flex flex-col gap-3 border-t border-slate-800 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
            <span className="text-sm text-slate-400">
              Page {auditPage.page + 1} of {totalPages}
            </span>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                disabled={currentPage === 0 || isLoading}
                                onClick={() => setCurrentPage((value) => Math.max(0, value - 1))}
                                className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                <FiChevronLeft className="h-4 w-4"/>
                                Previous
                            </button>
                            <button
                                type="button"
                                disabled={!getPaginatedHasNext(auditPage) || isLoading}
                                onClick={() => setCurrentPage((value) => value + 1)}
                                className="app-button-field button-hover-lift button-shadow-neutral inline-flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm font-semibold text-slate-200 disabled:cursor-not-allowed disabled:opacity-60"
                            >
                                Next
                                <FiChevronRight className="h-4 w-4"/>
                            </button>
                        </div>
                    </div>
                </motion.section>
            )}
        </div>
    );
};

export default InboxPage;
