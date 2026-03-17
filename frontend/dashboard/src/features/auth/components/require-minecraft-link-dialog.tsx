import { useMemo, useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
    Gamepad2Icon,
    LoaderCircleIcon,
    LogOutIcon,
    MessageSquareTextIcon,
    ShieldCheckIcon,
    type LucideIcon,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    minecraftLinkConfirmFormSchema,
    minecraftLinkRequestFormSchema,
    type MinecraftLinkConfirmFormValues,
    type MinecraftLinkRequestFormValues,
} from "@/features/auth/schemas";
import { useAccessToken } from "@/hooks/use-access-token";
import { confirmMinecraftLinkOtp, requestMinecraftLinkOtp } from "@/lib/api";
import { normalizeRole } from "@/lib/roles";
import { useAuthStore } from "@/store/auth-store";
import type { AuthSession } from "@/types/auth";

type LinkStep = "confirm-online" | "offline" | "enter-username" | "enter-otp";

const SummaryCard = ({
    description,
    icon: Icon,
    title,
}: {
    description: string;
    icon: LucideIcon;
    title: string;
}) => (
    <div className="rounded-xl border border-border/70 bg-background/55 p-3">
        <div className="flex items-start gap-3">
            <div className="rounded-lg border border-primary/20 bg-primary/10 p-2 text-primary">
                <Icon className="size-4" />
            </div>
            <div className="space-y-1">
                <div className="text-sm font-medium text-foreground">{title}</div>
                <div className="text-xs leading-5 text-muted-foreground">{description}</div>
            </div>
        </div>
    </div>
);

const RequireMinecraftLinkDialogContent = ({ session }: { session: AuthSession }) => {
    const updateUser = useAuthStore((state) => state.updateUser);
    const logout = useAuthStore((state) => state.logout);
    const getAccessToken = useAccessToken();

    const [linkStep, setLinkStep] = useState<LinkStep>("confirm-online");
    const [statusMessage, setStatusMessage] = useState<string | null>(null);

    const requestForm = useForm<MinecraftLinkRequestFormValues>({
        resolver: zodResolver(minecraftLinkRequestFormSchema),
        defaultValues: {
            minecraftUsername: "",
        },
    });
    const confirmForm = useForm<MinecraftLinkConfirmFormValues>({
        resolver: zodResolver(minecraftLinkConfirmFormSchema),
        defaultValues: {
            otp: "",
        },
    });

    const isBusy = requestForm.formState.isSubmitting || confirmForm.formState.isSubmitting;

    const stepBadgeLabel = useMemo(() => {
        if (linkStep === "enter-otp") {
            return "Verification";
        }

        if (linkStep === "enter-username") {
            return "In-game request";
        }

        return "Linked access required";
    }, [linkStep]);

    const handleRequestOtp = requestForm.handleSubmit(async (values) => {
        try {
            const accessToken = await getAccessToken();
            await requestMinecraftLinkOtp(accessToken, values.minecraftUsername.trim());

            setStatusMessage("A 6-digit code was sent to your in-game chat.");
            setLinkStep("enter-otp");
            confirmForm.reset({ otp: "" });
            toast.success("Verification code sent to Minecraft chat.");
        } catch (error) {
            const message =
                error instanceof Error ? error.message : "Unable to request a link code.";
            requestForm.setError("root", { message });
            toast.error(message);
        }
    });

    const handleConfirmOtp = confirmForm.handleSubmit(async (values) => {
        try {
            const accessToken = await getAccessToken();
            const updatedUser = await confirmMinecraftLinkOtp(accessToken, values.otp.trim());

            updateUser(updatedUser);
            toast.success("Minecraft account linked.");
        } catch (error) {
            const message =
                error instanceof Error ? error.message : "Unable to confirm the link code.";
            confirmForm.setError("root", { message });
            toast.error(message);
        }
    });

    const usernameField = requestForm.register("minecraftUsername", {
        onChange: () => {
            requestForm.clearErrors("root");
            setStatusMessage(null);
        },
    });
    const otpField = confirmForm.register("otp", {
        onChange: () => {
            confirmForm.clearErrors("root");
        },
        setValueAs: (value) =>
            typeof value === "string" ? value.replace(/\D/g, "").slice(0, 6) : "",
    });

    return (
        <Dialog open>
            <DialogContent
                showCloseButton={false}
                className="max-h-[92vh] gap-0 overflow-hidden p-0 sm:max-w-4xl"
                onEscapeKeyDown={(event) => event.preventDefault()}
                onInteractOutside={(event) => event.preventDefault()}
            >
                <DialogHeader className="border-b border-border/70 px-6 py-5">
                    <div className="flex flex-wrap items-center gap-2">
                        <Badge variant="outline" className="border-primary/25 bg-primary/10 text-primary">
                            {stepBadgeLabel}
                        </Badge>
                        <Badge variant="outline" className="border-border/80">
                            {session.user.email}
                        </Badge>
                    </div>
                    <DialogTitle className="text-xl">Connect your Minecraft account</DialogTitle>
                    <DialogDescription className="max-w-2xl">
                        Player-specific actions stay locked until this dashboard session is linked to
                        an in-game Minecraft account.
                    </DialogDescription>
                </DialogHeader>

                <div className="grid gap-4 overflow-y-auto px-6 py-5 lg:grid-cols-[300px_minmax(0,1fr)]">
                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader>
                            <CardTitle className="text-base">Current session</CardTitle>
                            <CardDescription>
                                Link verification is required for player transfers and account-aware
                                moderation actions.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            <SummaryCard
                                icon={ShieldCheckIcon}
                                title={session.user.username}
                                description="Authenticated dashboard user that is requesting the link."
                            />
                            <SummaryCard
                                icon={Gamepad2Icon}
                                title="In-game verification"
                                description="The API sends a one-time code to Minecraft chat on the target account."
                            />
                            <SummaryCard
                                icon={MessageSquareTextIcon}
                                title="One active step"
                                description="Complete the request and confirmation flow from this dialog before returning to the shell."
                            />

                            <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                                <div className="text-xs uppercase tracking-[0.24em] text-muted-foreground">
                                    Link progress
                                </div>
                                <div className="mt-2 text-sm font-medium text-foreground">
                                    {linkStep === "enter-otp"
                                        ? "Awaiting confirmation code"
                                        : linkStep === "enter-username"
                                          ? "Ready to request in-game code"
                                          : "Needs operator confirmation"}
                                </div>
                            </div>

                            <Button
                                type="button"
                                variant="outline"
                                className="w-full"
                                disabled={isBusy}
                                onClick={() => logout()}
                            >
                                <LogOutIcon className="size-4" />
                                Sign out instead
                            </Button>
                        </CardContent>
                    </Card>

                    <Card className="border border-border/70 bg-card/85 shadow-none">
                        <CardHeader>
                            <CardTitle className="text-base">Link workflow</CardTitle>
                            <CardDescription>
                                Confirm you are online in Minecraft, request the verification code,
                                then enter the 6-digit code shown in chat.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            {linkStep === "confirm-online" ? (
                                <div className="space-y-4">
                                    <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-4 text-sm text-muted-foreground">
                                        Are you currently online on the Minecraft server with the
                                        account you want to link?
                                    </div>
                                    <div className="flex flex-col gap-3 sm:flex-row">
                                        <Button
                                            type="button"
                                            onClick={() => setLinkStep("enter-username")}
                                        >
                                            Yes, I&apos;m online
                                        </Button>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            onClick={() => setLinkStep("offline")}
                                        >
                                            Not right now
                                        </Button>
                                    </div>
                                </div>
                            ) : null}

                            {linkStep === "offline" ? (
                                <div className="space-y-4">
                                    <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-sm text-amber-100">
                                        The verification code is delivered in Minecraft chat, so the
                                        target account must be online before the link can complete.
                                    </div>
                                    <div className="flex flex-col gap-3 sm:flex-row">
                                        <Button
                                            type="button"
                                            onClick={() => setLinkStep("confirm-online")}
                                        >
                                            I&apos;m online now
                                        </Button>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            disabled={isBusy}
                                            onClick={() => logout()}
                                        >
                                            <LogOutIcon className="size-4" />
                                            Sign out
                                        </Button>
                                    </div>
                                </div>
                            ) : null}

                            {linkStep === "enter-username" ? (
                                <form className="space-y-4" onSubmit={(event) => void handleRequestOtp(event)}>
                                    <Field>
                                        <FieldLabel htmlFor="minecraft-username">
                                            Minecraft username
                                        </FieldLabel>
                                        <Input
                                            id="minecraft-username"
                                            placeholder="Enter your Minecraft username"
                                            aria-invalid={
                                                requestForm.formState.errors.minecraftUsername
                                                    ? "true"
                                                    : "false"
                                            }
                                            {...usernameField}
                                        />
                                        <FieldDescription>
                                            The API will deliver a one-time code to this account in
                                            chat if it is online right now.
                                        </FieldDescription>
                                        <FieldError
                                            errors={[
                                                requestForm.formState.errors.minecraftUsername,
                                            ]}
                                        />
                                    </Field>

                                    {statusMessage ? (
                                        <div className="rounded-xl border border-primary/25 bg-primary/10 px-4 py-3 text-sm text-primary">
                                            {statusMessage}
                                        </div>
                                    ) : null}

                                    <FieldError>{requestForm.formState.errors.root?.message}</FieldError>

                                    <div className="flex flex-col gap-3 sm:flex-row">
                                        <Button
                                            type="submit"
                                            disabled={requestForm.formState.isSubmitting}
                                        >
                                            {requestForm.formState.isSubmitting ? (
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                            ) : null}
                                            Send link code
                                        </Button>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            disabled={requestForm.formState.isSubmitting}
                                            onClick={() => setLinkStep("confirm-online")}
                                        >
                                            Back
                                        </Button>
                                    </div>
                                </form>
                            ) : null}

                            {linkStep === "enter-otp" ? (
                                <form className="space-y-4" onSubmit={(event) => void handleConfirmOtp(event)}>
                                    <div className="rounded-xl border border-primary/25 bg-primary/10 px-4 py-4 text-sm text-primary">
                                        {statusMessage ??
                                            "A verification code was sent to Minecraft chat. Enter it below to complete the link."}
                                    </div>

                                    <Field>
                                        <FieldLabel htmlFor="minecraft-link-otp">6-digit code</FieldLabel>
                                        <Input
                                            id="minecraft-link-otp"
                                            inputMode="numeric"
                                            placeholder="123456"
                                            aria-invalid={
                                                confirmForm.formState.errors.otp ? "true" : "false"
                                            }
                                            {...otpField}
                                        />
                                        <FieldDescription>
                                            Only numeric input is accepted in this field.
                                        </FieldDescription>
                                        <FieldError errors={[confirmForm.formState.errors.otp]} />
                                    </Field>

                                    <FieldError>{confirmForm.formState.errors.root?.message}</FieldError>

                                    <div className="flex flex-col gap-3 sm:flex-row">
                                        <Button
                                            type="submit"
                                            disabled={confirmForm.formState.isSubmitting}
                                        >
                                            {confirmForm.formState.isSubmitting ? (
                                                <LoaderCircleIcon className="size-4 animate-spin" />
                                            ) : null}
                                            Confirm link
                                        </Button>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            disabled={confirmForm.formState.isSubmitting}
                                            onClick={() => setLinkStep("enter-username")}
                                        >
                                            Back
                                        </Button>
                                    </div>
                                </form>
                            ) : null}
                        </CardContent>
                    </Card>
                </div>
            </DialogContent>
        </Dialog>
    );
};

const RequireMinecraftLinkDialog = () => {
    const session = useAuthStore((state) => state.session);
    const normalizedUserRole = normalizeRole(session?.user.role);
    const isServiceRole = normalizedUserRole === "service";
    const linkedPlayerUuid = session?.user.linkedPlayerUuid?.trim() ?? "";

    if (!session || isServiceRole || linkedPlayerUuid) {
        return null;
    }

    return <RequireMinecraftLinkDialogContent key={session.refreshToken} session={session} />;
};

export default RequireMinecraftLinkDialog;
