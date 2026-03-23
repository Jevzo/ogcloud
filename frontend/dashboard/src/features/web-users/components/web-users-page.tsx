import {
    Clock3Icon,
    LoaderCircleIcon,
    SearchIcon,
    ShieldAlertIcon,
    UserPlusIcon,
} from "lucide-react";
import { useDeferredValue, useState } from "react";
import { toast } from "sonner";

import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import { PageReveal, RevealGroup } from "@/components/ui/page-reveal";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { createWebUser, deleteWebUser, unlinkWebUserAccount, updateWebUser } from "@/api";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { hasAdminAccess, normalizeRole } from "@/features/auth/lib/roles";
import CreateWebUserDialog from "@/features/web-users/components/create-web-user-dialog";
import EditWebUserDialog from "@/features/web-users/components/edit-web-user-dialog";
import WebUserActionsMenu from "@/features/web-users/components/web-user-actions-menu";
import { useWebUsersQuery } from "@/features/web-users/hooks/use-web-users-query";
import { formatDateTime } from "@/features/servers/lib/server-display";
import type {
    CreateWebUserFormValues,
    UpdateWebUserFormValues,
} from "@/features/web-users/schemas";
import { useAuthStore } from "@/store/auth-store";
import { getPaginatedHasNext, getPaginatedTotalPages } from "@/types/dashboard";
import type { UpdateWebUserPayload, WebUserRecord } from "@/types/web-user";

const getRoleBadgeClassName = (role: string) => {
    const normalizedRole = normalizeRole(role);

    if (normalizedRole === "admin") {
        return "border-red-500/30 bg-red-500/10 text-red-300";
    }

    if (normalizedRole === "service") {
        return "border-amber-500/30 bg-amber-500/10 text-amber-300";
    }

    return "border-primary/25 bg-primary/10 text-primary";
};

const SummaryCard = ({
    helper,
    label,
    value,
}: {
    helper: string;
    label: string;
    value: string;
}) => (
    <Card className="border border-border/70 bg-card/85 shadow-none">
        <CardHeader className="pb-3">
            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                {label}
            </CardDescription>
            <CardTitle className="text-xl tracking-tight">{value}</CardTitle>
        </CardHeader>
        <CardContent className="pt-0 text-sm text-muted-foreground">{helper}</CardContent>
    </Card>
);

const LastSyncSurface = ({
    isRefreshing,
    lastUpdatedAt,
}: {
    isRefreshing: boolean;
    lastUpdatedAt: number | null;
}) => (
    <div className="flex min-h-10 items-center gap-2 rounded-lg border border-border/70 bg-card/70 px-3 text-sm text-muted-foreground">
        {isRefreshing ? (
            <LoaderCircleIcon className="size-4 animate-spin text-primary" />
        ) : (
            <Clock3Icon className="size-4 text-primary" />
        )}
        <span>
            {lastUpdatedAt
                ? `Last sync ${formatDateTime(new Date(lastUpdatedAt).toISOString())}`
                : "Waiting for first sync"}
        </span>
    </div>
);

const WebUsersPage = () => {
    const getAccessToken = useAccessToken();
    const session = useAuthStore((state) => state.session);
    const updateUser = useAuthStore((state) => state.updateUser);

    const canManageWebUsers = hasAdminAccess(session?.user.role);
    const normalizedCurrentUserEmail = session?.user.email.trim().toLowerCase() ?? "";

    const [currentPage, setCurrentPage] = useState(0);
    const [searchInput, setSearchInput] = useState("");
    const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
    const [editTarget, setEditTarget] = useState<WebUserRecord | null>(null);
    const [deleteTarget, setDeleteTarget] = useState<WebUserRecord | null>(null);
    const [isCreating, setIsCreating] = useState(false);
    const [isUpdating, setIsUpdating] = useState(false);
    const [isDeleting, setIsDeleting] = useState(false);
    const [activeUnlinkEmail, setActiveUnlinkEmail] = useState<string | null>(null);

    const deferredQuery = useDeferredValue(searchInput.trim());
    const {
        data: webUserPage,
        errorMessage,
        isLoading,
        isRefreshing,
        lastUpdatedAt,
        refresh,
    } = useWebUsersQuery({
        currentPage,
        enabled: canManageWebUsers,
        query: deferredQuery,
    });

    const totalPages = getPaginatedTotalPages(webUserPage);
    const hasFreshData = lastUpdatedAt !== null;
    const linkedAccounts = webUserPage.items.filter((user) => user.linkedPlayerUuid).length;
    const privilegedAccounts = webUserPage.items.filter((user) => hasAdminAccess(user.role)).length;
    const visibleRoles = new Set(webUserPage.items.map((user) => user.role)).size;

    const handleCreate = async (values: CreateWebUserFormValues) => {
        setIsCreating(true);

        try {
            const accessToken = await getAccessToken();
            const createdUser = await createWebUser(accessToken, {
                email: values.email.trim().toLowerCase(),
                password: values.password,
                role: values.role,
            });

            toast.success(`Created web user ${createdUser.email}.`);
            setIsCreateDialogOpen(false);

            if (currentPage !== 0) {
                setCurrentPage(0);
            } else {
                await refresh(false);
            }
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to create web user.");
        } finally {
            setIsCreating(false);
        }
    };

    const handleUpdate = async (values: UpdateWebUserFormValues) => {
        if (!editTarget) {
            return;
        }

        const payload: UpdateWebUserPayload = {};
        const normalizedEmail = values.email.trim().toLowerCase();
        const normalizedUsername = values.username.trim();
        const normalizedPassword = values.password.trim();

        if (normalizedEmail !== editTarget.email) {
            payload.email = normalizedEmail;
        }

        if (normalizedUsername !== editTarget.username) {
            payload.username = normalizedUsername;
        }

        if (normalizedPassword) {
            payload.password = values.password;
        }

        if (values.role !== editTarget.role) {
            payload.role = values.role;
        }

        if (Object.keys(payload).length === 0) {
            toast.error("No changes to update.");
            return;
        }

        setIsUpdating(true);

        try {
            const accessToken = await getAccessToken();
            const updatedUser = await updateWebUser(accessToken, editTarget.email, payload);

            if (normalizedCurrentUserEmail === editTarget.email.trim().toLowerCase()) {
                updateUser(updatedUser);
            }

            toast.success(`Updated web user ${updatedUser.email}.`);
            setEditTarget(null);
            await refresh(false);
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to update web user.");
        } finally {
            setIsUpdating(false);
        }
    };

    const handleDelete = async () => {
        if (!deleteTarget) {
            return;
        }

        setIsDeleting(true);

        try {
            const accessToken = await getAccessToken();
            await deleteWebUser(accessToken, deleteTarget.email);
            toast.success(`Deleted web user ${deleteTarget.email}.`);
            setDeleteTarget(null);

            if (webUserPage.items.length === 1 && currentPage > 0) {
                setCurrentPage((value) => Math.max(0, value - 1));
            } else {
                await refresh(false);
            }
        } catch (error) {
            toast.error(error instanceof Error ? error.message : "Unable to delete web user.");
        } finally {
            setIsDeleting(false);
        }
    };

    const handleUnlink = async (user: WebUserRecord) => {
        if (!user.linkedPlayerUuid) {
            return;
        }

        const normalizedTargetEmail = user.email.trim().toLowerCase();
        setActiveUnlinkEmail(normalizedTargetEmail);

        try {
            const accessToken = await getAccessToken();
            const updatedUser = await unlinkWebUserAccount(accessToken, user.email);

            if (normalizedCurrentUserEmail === normalizedTargetEmail) {
                updateUser(updatedUser);
            }

            toast.success(`Unlinked Minecraft account for ${updatedUser.email}.`);
            await refresh(false);
        } catch (error) {
            toast.error(
                error instanceof Error ? error.message : "Unable to unlink Minecraft account.",
            );
        } finally {
            setActiveUnlinkEmail(null);
        }
    };

    if (!canManageWebUsers) {
        return (
            <PageReveal className="space-y-4">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Dashboard access
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Dashboard accounts, role assignments, and linked Minecraft identities for
                        your operators.
                    </p>
                </div>

                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader>
                        <CardTitle className="flex items-center gap-2 text-amber-200">
                            <ShieldAlertIcon className="size-4" />
                            Restricted
                        </CardTitle>
                        <CardDescription className="text-amber-100/80">
                            Only admin and service accounts can manage web users.
                        </CardDescription>
                    </CardHeader>
                </Card>
            </PageReveal>
        );
    }

    if (isLoading && !hasFreshData) {
        return null;
    }

    if (errorMessage && !hasFreshData) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardDescription className="text-xs uppercase tracking-[0.24em] text-destructive">
                        Web Users Error
                    </CardDescription>
                    <CardTitle className="text-destructive">
                        Unable to load dashboard accounts
                    </CardTitle>
                    <CardDescription className="text-sm text-destructive/80">
                        {errorMessage}
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    return (
        <PageReveal className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight text-foreground">
                        Dashboard access
                    </h1>
                    <p className="mt-2 max-w-3xl text-sm text-muted-foreground">
                        Dashboard accounts, role assignments, and linked Minecraft identities for
                        your operators.
                    </p>
                </div>

                <div className="flex flex-col gap-3 sm:flex-row sm:items-center lg:justify-end">
                    <Button onClick={() => setIsCreateDialogOpen(true)}>
                        <UserPlusIcon className="size-4" />
                        Create user
                    </Button>
                    <LastSyncSurface isRefreshing={isRefreshing} lastUpdatedAt={lastUpdatedAt} />
                </div>
            </div>

            {errorMessage && hasFreshData ? (
                <Card className="border border-amber-500/30 bg-amber-500/10 shadow-none">
                    <CardHeader className="pb-3">
                        <CardTitle className="text-base text-amber-200">
                            Showing the latest successful account snapshot
                        </CardTitle>
                        <CardDescription className="text-sm text-amber-100/80">
                            {errorMessage}
                        </CardDescription>
                    </CardHeader>
                </Card>
            ) : null}

            <RevealGroup className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryCard
                    label="Total accounts"
                    value={`${webUserPage.totalItems}`}
                    helper="Backend paginated dashboard accounts visible to the current query."
                />
                <SummaryCard
                    label="Linked on page"
                    value={`${linkedAccounts}`}
                    helper="Accounts on the current page with an active Minecraft account link."
                />
                <SummaryCard
                    label="Privileged on page"
                    value={`${privilegedAccounts}`}
                    helper="Admin and service accounts represented in the rendered rows."
                />
                <SummaryCard
                    label="Roles on page"
                    value={`${visibleRoles}`}
                    helper="Distinct dashboard roles represented on the current page."
                />
            </RevealGroup>

            <Card className="border border-border/70 bg-card/85 shadow-none">
                <CardHeader className="gap-4 border-b border-border/70 pb-4">
                    <div className="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                        <div>
                            <CardDescription className="text-xs uppercase tracking-[0.24em]">
                                Account inventory
                            </CardDescription>
                            <CardTitle className="text-base">Dashboard users</CardTitle>
                            <CardDescription>
                                Search by email, username, or role, then manage the selected account
                                from the row action menu.
                            </CardDescription>
                        </div>

                        <div className="w-full xl:max-w-[320px]">
                            <InputGroup>
                                <InputGroupAddon>
                                    <SearchIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    className="text-[13px] placeholder:text-[13px]"
                                    value={searchInput}
                                    onChange={(event) => {
                                        setSearchInput(event.target.value);
                                        setCurrentPage(0);
                                    }}
                                    placeholder="Search email, username, or role"
                                />
                            </InputGroup>
                        </div>
                    </div>
                </CardHeader>

                <CardContent className="px-0">
                    <Table>
                        <TableHeader>
                            <TableRow className="hover:bg-transparent">
                                <TableHead className="px-4">Email</TableHead>
                                <TableHead className="px-4">Username</TableHead>
                                <TableHead className="px-4">Role</TableHead>
                                <TableHead className="px-4">Linked player</TableHead>
                                <TableHead className="px-4 text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {webUserPage.items.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={5}
                                        className="px-4 py-12 text-center text-sm text-muted-foreground"
                                    >
                                        No web users matched the current query.
                                    </TableCell>
                                </TableRow>
                            ) : (
                                webUserPage.items.map((user) => {
                                    const normalizedTargetEmail = user.email.trim().toLowerCase();
                                    const isUnlinking = activeUnlinkEmail === normalizedTargetEmail;
                                    const isBusy =
                                        isUpdating || isDeleting || activeUnlinkEmail !== null;

                                    return (
                                        <TableRow key={user.id}>
                                            <TableCell className="px-4 py-3 align-top">
                                                <div className="space-y-1">
                                                    <div className="font-medium text-foreground">
                                                        {user.email}
                                                    </div>
                                                    {normalizedTargetEmail ===
                                                    normalizedCurrentUserEmail ? (
                                                        <div className="text-xs text-muted-foreground">
                                                            Current session account
                                                        </div>
                                                    ) : null}
                                                </div>
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top text-muted-foreground">
                                                {user.username}
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top">
                                                <Badge
                                                    variant="outline"
                                                    className={getRoleBadgeClassName(user.role)}
                                                >
                                                    {user.role}
                                                </Badge>
                                            </TableCell>
                                            <TableCell className="px-4 py-3 align-top">
                                                {user.linkedPlayerUuid ? (
                                                    <div className="space-y-1">
                                                        <Badge
                                                            variant="outline"
                                                            className="border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                        >
                                                            Linked
                                                        </Badge>
                                                        <div className="font-mono text-xs text-muted-foreground">
                                                            {user.linkedPlayerUuid}
                                                        </div>
                                                    </div>
                                                ) : (
                                                    <Badge
                                                        variant="outline"
                                                        className="border-border/80"
                                                    >
                                                        Unlinked
                                                    </Badge>
                                                )}
                                            </TableCell>
                                            <TableCell className="px-4 py-3 text-right align-top">
                                                <WebUserActionsMenu
                                                    disabled={isBusy}
                                                    isUnlinking={isUnlinking}
                                                    onDelete={setDeleteTarget}
                                                    onEdit={setEditTarget}
                                                    onUnlink={(nextUser) => {
                                                        void handleUnlink(nextUser);
                                                    }}
                                                    user={user}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    );
                                })
                            )}
                        </TableBody>
                    </Table>
                </CardContent>

                <CardFooter className="justify-between gap-3">
                    <div className="text-sm text-muted-foreground">
                        Page {webUserPage.page + 1} of {totalPages}
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            onClick={() => setCurrentPage((value) => Math.max(0, value - 1))}
                            disabled={currentPage === 0 || isRefreshing}
                        >
                            Previous
                        </Button>
                        <Button
                            variant="outline"
                            onClick={() => setCurrentPage((value) => value + 1)}
                            disabled={!getPaginatedHasNext(webUserPage) || isRefreshing}
                        >
                            Next
                        </Button>
                    </div>
                </CardFooter>
            </Card>

            <CreateWebUserDialog
                isSubmitting={isCreating}
                open={isCreateDialogOpen}
                onOpenChange={setIsCreateDialogOpen}
                onSubmit={handleCreate}
            />

            <EditWebUserDialog
                isSubmitting={isUpdating}
                open={editTarget !== null}
                targetUser={editTarget}
                onOpenChange={(open) => {
                    if (!open) {
                        setEditTarget(null);
                    }
                }}
                onSubmit={handleUpdate}
            />

            <AlertDialog
                open={deleteTarget !== null}
                onOpenChange={(open) => {
                    if (!open) {
                        setDeleteTarget(null);
                    }
                }}
            >
                <AlertDialogContent>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Delete web user</AlertDialogTitle>
                        <AlertDialogDescription>
                            {deleteTarget
                                ? `Delete ${deleteTarget.email}? This removes the dashboard account and revokes active sessions.`
                                : "Delete the selected dashboard account."}
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                            variant="destructive"
                            disabled={isDeleting}
                            onClick={(event) => {
                                event.preventDefault();
                                void handleDelete();
                            }}
                        >
                            {isDeleting ? (
                                <LoaderCircleIcon className="size-4 animate-spin" />
                            ) : null}
                            Delete user
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </PageReveal>
    );
};

export default WebUsersPage;
