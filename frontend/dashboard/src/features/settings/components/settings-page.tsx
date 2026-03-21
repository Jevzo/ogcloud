import { zodResolver } from "@hookform/resolvers/zod";
import {
    AtSignIcon,
    FingerprintIcon,
    KeyRoundIcon,
    Link2Icon,
    LoaderCircleIcon,
    MailIcon,
    ShieldCheckIcon,
    UserRoundIcon,
    type LucideIcon,
} from "lucide-react";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    settingsEmailFormSchema,
    settingsPasswordFormSchema,
    type SettingsEmailFormValues,
    type SettingsPasswordFormValues,
} from "@/features/settings/schemas";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { loginWithEmailPassword, updateOwnProfile } from "@/api";
import { normalizeRole } from "@/features/auth/lib/roles";
import { useAuthStore } from "@/store/auth-store";

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

const formatRoleLabel = (role: string) => {
    const normalizedRole = normalizeRole(role);

    if (!normalizedRole) {
        return "Unknown";
    }

    return normalizedRole.charAt(0).toUpperCase() + normalizedRole.slice(1);
};

const SummaryItem = ({
    helper,
    icon: Icon,
    label,
    value,
}: {
    helper: string;
    icon: LucideIcon;
    label: string;
    value: string;
}) => (
    <div className="rounded-xl border border-border/70 bg-background/40 p-3">
        <div className="flex items-start gap-3">
            <div className="rounded-lg border border-primary/20 bg-primary/10 p-2 text-primary">
                <Icon className="size-4" />
            </div>
            <div className="min-w-0 space-y-1">
                <div className="text-xs uppercase tracking-[0.24em] text-muted-foreground">
                    {label}
                </div>
                <div className="truncate text-sm font-medium text-foreground">{value}</div>
                <div className="text-xs text-muted-foreground">{helper}</div>
            </div>
        </div>
    </div>
);

const SettingsPage = () => {
    const currentUser = useAuthStore((state) => state.session?.user ?? null);
    const setSession = useAuthStore((state) => state.setSession);
    const updateUser = useAuthStore((state) => state.updateUser);
    const getAccessToken = useAccessToken();

    const linkedPlayerHeadUrl = currentUser?.linkedPlayerUuid
        ? `https://mc-heads.net/avatar/${currentUser.linkedPlayerUuid}`
        : null;

    const emailForm = useForm<SettingsEmailFormValues>({
        resolver: zodResolver(settingsEmailFormSchema),
        defaultValues: {
            email: currentUser?.email ?? "",
        },
    });
    const passwordForm = useForm<SettingsPasswordFormValues>({
        resolver: zodResolver(settingsPasswordFormSchema),
        defaultValues: {
            confirmPassword: "",
            password: "",
        },
    });

    useEffect(() => {
        emailForm.reset({
            email: currentUser?.email ?? "",
        });
    }, [currentUser?.email, emailForm]);

    if (!currentUser) {
        return (
            <Card className="border border-destructive/40 bg-destructive/5 shadow-none">
                <CardHeader>
                    <CardTitle className="text-destructive">Account context unavailable</CardTitle>
                    <CardDescription className="text-destructive/80">
                        The dashboard could not load the authenticated user profile for this
                        session.
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    const handleEmailSubmit = emailForm.handleSubmit(async (values) => {
        const normalizedEmail = values.email.trim().toLowerCase();
        const currentEmail = currentUser.email.trim().toLowerCase();

        if (normalizedEmail === currentEmail) {
            toast.info("Your email is already up to date.");
            return;
        }

        try {
            const accessToken = await getAccessToken();
            const updatedUser = await updateOwnProfile(accessToken, { email: normalizedEmail });

            updateUser(updatedUser);
            emailForm.reset({ email: updatedUser.email });
            toast.success("Email updated.");
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to update email.";
            emailForm.setError("root", { message });
            toast.error(message);
        }
    });

    const handlePasswordSubmit = passwordForm.handleSubmit(async (values) => {
        try {
            const accessToken = await getAccessToken();
            await updateOwnProfile(accessToken, { password: values.password });

            const nextSession = await loginWithEmailPassword({
                email: currentUser.email,
                password: values.password,
            });

            setSession(nextSession);
            passwordForm.reset({
                confirmPassword: "",
                password: "",
            });
            toast.success("Password updated and session refreshed.");
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to update password.";
            passwordForm.setError("root", { message });
            toast.error(message);
        }
    });

    const emailField = emailForm.register("email", {
        onChange: () => emailForm.clearErrors("root"),
    });
    const passwordField = passwordForm.register("password", {
        onChange: () => passwordForm.clearErrors("root"),
    });
    const confirmPasswordField = passwordForm.register("confirmPassword", {
        onChange: () => passwordForm.clearErrors("root"),
    });

    return (
        <div className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                    <Badge
                        variant="outline"
                        className="w-fit border-primary/25 bg-primary/10 text-primary"
                    >
                        Account settings
                    </Badge>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight text-foreground">
                            Settings
                        </h1>
                        <p className="max-w-3xl text-sm text-muted-foreground">
                            Review your authenticated dashboard profile, rotate credentials, and
                            confirm the Minecraft account link attached to this operator session.
                        </p>
                    </div>
                </div>

                <Badge variant="outline" className={getRoleBadgeClassName(currentUser.role)}>
                    {formatRoleLabel(currentUser.role)}
                </Badge>
            </div>

            <div className="grid gap-4 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
                <div className="space-y-4">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader>
                            <CardTitle className="text-base">Account overview</CardTitle>
                            <CardDescription>
                                Read-only identity details for the currently authenticated dashboard
                                account.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="grid gap-3 md:grid-cols-2">
                            <SummaryItem
                                helper="Username stored on the active dashboard session."
                                icon={UserRoundIcon}
                                label="Username"
                                value={currentUser.username}
                            />
                            <SummaryItem
                                helper="Primary email address used for sign-in."
                                icon={MailIcon}
                                label="Email"
                                value={currentUser.email}
                            />
                            <SummaryItem
                                helper="Role currently applied to route and feature access checks."
                                icon={ShieldCheckIcon}
                                label="Role"
                                value={formatRoleLabel(currentUser.role)}
                            />
                            <SummaryItem
                                helper="Linked player UUID availability for in-game operations."
                                icon={FingerprintIcon}
                                label="Minecraft link"
                                value={currentUser.linkedPlayerUuid ? "Linked" : "Required"}
                            />
                        </CardContent>
                    </Card>

                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader>
                            <CardTitle className="text-base">Minecraft account</CardTitle>
                            <CardDescription>
                                This link is used for player-specific actions and direct account
                                transfer flows inside the dashboard shell.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                                {linkedPlayerHeadUrl ? (
                                    <img
                                        src={linkedPlayerHeadUrl}
                                        alt={`${currentUser.username} Minecraft avatar`}
                                        className="size-20 rounded-2xl border border-border/70 bg-background/60 object-cover"
                                        referrerPolicy="no-referrer"
                                    />
                                ) : (
                                    <div className="flex size-20 items-center justify-center rounded-2xl border border-border/70 bg-background/60 text-primary">
                                        <Link2Icon className="size-8" />
                                    </div>
                                )}

                                <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <Badge
                                            variant="outline"
                                            className={
                                                currentUser.linkedPlayerUuid
                                                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                                    : "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                            }
                                        >
                                            {currentUser.linkedPlayerUuid ? "Linked" : "Not linked"}
                                        </Badge>
                                        <div className="text-sm font-medium text-foreground">
                                            {currentUser.username}
                                        </div>
                                    </div>
                                    <p className="text-sm text-muted-foreground">
                                        {currentUser.linkedPlayerUuid
                                            ? "Your Minecraft account is linked and available to protected player actions."
                                            : "A Minecraft account link is required before protected player actions can be used."}
                                    </p>
                                </div>
                            </div>

                            {currentUser.linkedPlayerUuid ? (
                                <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                                    <div className="text-xs uppercase tracking-[0.24em] text-muted-foreground">
                                        Linked player UUID
                                    </div>
                                    <div className="mt-2 break-all font-mono text-sm text-foreground">
                                        {currentUser.linkedPlayerUuid}
                                    </div>
                                </div>
                            ) : (
                                <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">
                                    The shell will prompt for in-game verification until a Minecraft
                                    account is linked.
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </div>

                <div className="space-y-4">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2 text-base">
                                <AtSignIcon className="size-4 text-primary" />
                                Profile email
                            </CardTitle>
                            <CardDescription>
                                Update the email address used for password-based dashboard access.
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            <form
                                className="space-y-4"
                                onSubmit={(event) => void handleEmailSubmit(event)}
                            >
                                <FieldGroup>
                                    <Field>
                                        <FieldLabel htmlFor="settings-email">
                                            Email address
                                        </FieldLabel>
                                        <Input
                                            id="settings-email"
                                            type="email"
                                            autoComplete="email"
                                            aria-invalid={
                                                emailForm.formState.errors.email ? "true" : "false"
                                            }
                                            {...emailField}
                                        />
                                        <FieldDescription>
                                            Updates apply to future sign-ins immediately after save.
                                        </FieldDescription>
                                        <FieldError errors={[emailForm.formState.errors.email]} />
                                    </Field>
                                </FieldGroup>

                                <FieldError>{emailForm.formState.errors.root?.message}</FieldError>

                                <div className="flex justify-end">
                                    <Button
                                        type="submit"
                                        disabled={emailForm.formState.isSubmitting}
                                    >
                                        {emailForm.formState.isSubmitting ? (
                                            <>
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                                Saving
                                            </>
                                        ) : (
                                            "Save email"
                                        )}
                                    </Button>
                                </div>
                            </form>
                        </CardContent>
                    </Card>

                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2 text-base">
                                <KeyRoundIcon className="size-4 text-primary" />
                                Password rotation
                            </CardTitle>
                            <CardDescription>
                                Change the password for this account and refresh the current session
                                with the new credentials.
                            </CardDescription>
                        </CardHeader>
                        <CardContent>
                            <form
                                className="space-y-4"
                                onSubmit={(event) => void handlePasswordSubmit(event)}
                            >
                                <FieldGroup>
                                    <Field>
                                        <FieldLabel htmlFor="settings-password">
                                            New password
                                        </FieldLabel>
                                        <Input
                                            id="settings-password"
                                            type="password"
                                            autoComplete="new-password"
                                            aria-invalid={
                                                passwordForm.formState.errors.password
                                                    ? "true"
                                                    : "false"
                                            }
                                            {...passwordField}
                                        />
                                        <FieldDescription>
                                            The dashboard will re-authenticate the current session
                                            after the password changes.
                                        </FieldDescription>
                                        <FieldError
                                            errors={[passwordForm.formState.errors.password]}
                                        />
                                    </Field>

                                    <Field>
                                        <FieldLabel htmlFor="settings-confirm-password">
                                            Confirm new password
                                        </FieldLabel>
                                        <Input
                                            id="settings-confirm-password"
                                            type="password"
                                            autoComplete="new-password"
                                            aria-invalid={
                                                passwordForm.formState.errors.confirmPassword
                                                    ? "true"
                                                    : "false"
                                            }
                                            {...confirmPasswordField}
                                        />
                                        <FieldError
                                            errors={[passwordForm.formState.errors.confirmPassword]}
                                        />
                                    </Field>
                                </FieldGroup>

                                <FieldError>
                                    {passwordForm.formState.errors.root?.message}
                                </FieldError>

                                <div className="flex justify-end">
                                    <Button
                                        type="submit"
                                        disabled={passwordForm.formState.isSubmitting}
                                    >
                                        {passwordForm.formState.isSubmitting ? (
                                            <>
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                                Saving
                                            </>
                                        ) : (
                                            "Save password"
                                        )}
                                    </Button>
                                </div>
                            </form>
                        </CardContent>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default SettingsPage;
