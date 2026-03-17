import { startTransition, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { EyeIcon, EyeOffIcon, Loader2Icon, LockKeyholeIcon, MailIcon } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupButton,
    InputGroupInput,
} from "@/components/ui/input-group";
import { useAuthStore } from "@/store/auth-store";

const loginSchema = z.object({
    email: z.string().email("Enter a valid email address."),
    password: z.string().min(1, "Password is required."),
});

type LoginFormValues = z.infer<typeof loginSchema>;

const LoginPage = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);
    const status = useAuthStore((state) => state.status);
    const [showPassword, setShowPassword] = useState(false);

    const form = useForm<LoginFormValues>({
        resolver: zodResolver(loginSchema),
        defaultValues: {
            email: "",
            password: "",
        },
    });

    const emailField = form.register("email", {
        onChange: () => form.clearErrors("root"),
    });
    const passwordField = form.register("password", {
        onChange: () => form.clearErrors("root"),
    });

    const handleSubmit = form.handleSubmit(async (values) => {
        try {
            await login({
                email: values.email.trim(),
                password: values.password,
            });

            const nextState = location.state as { from?: string } | null;
            const nextPath = typeof nextState?.from === "string" ? nextState.from : "/";

            startTransition(() => {
                navigate(nextPath, { replace: true });
            });
        } catch (error) {
            const message =
                error instanceof Error ? error.message : "Unable to sign in. Please try again.";

            form.setError("root", { message });
            toast.error(message);
        }
    });

    const isSubmitting = form.formState.isSubmitting || status === "authenticating";

    return (
        <div className="relative flex min-h-screen overflow-hidden">
            <div
                className="pointer-events-none absolute inset-0"
                aria-hidden="true"
            >
                <div className="absolute inset-x-0 top-0 h-56 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.28),transparent_58%)]" />
                <div className="absolute -left-24 top-20 h-72 w-72 rounded-full bg-blue-500/10 blur-3xl" />
                <div className="absolute -right-16 bottom-12 h-72 w-72 rounded-full bg-sky-500/10 blur-3xl" />
            </div>

            <div className="relative grid w-full gap-6 px-4 py-8 lg:grid-cols-[1.1fr_0.9fr] lg:px-10 lg:py-10">
                <section className="hidden rounded-[2rem] border border-border/70 bg-card/55 p-8 shadow-2xl shadow-black/10 backdrop-blur lg:flex lg:flex-col lg:justify-between">
                    <div>
                        <div className="flex items-center gap-4">
                            <img src="/static/logo.webp" alt="OgCloud" className="h-12 w-12 rounded-2xl" />
                            <div>
                                <p className="text-sm font-semibold uppercase tracking-[0.24em] text-primary">
                                    OgCloud
                                </p>
                                <h1 className="mt-2 max-w-lg text-4xl leading-tight font-semibold text-foreground">
                                    Operate servers, groups, players, and network policy from one shell.
                                </h1>
                            </div>
                        </div>
                        <p className="mt-6 max-w-xl text-base leading-7 text-muted-foreground">
                            The dashboard is optimized for dense, operational workflows: runtime state,
                            scaling controls, permission management, and network-wide actions stay one
                            click away.
                        </p>
                    </div>

                    <div className="grid gap-4 xl:grid-cols-3">
                        <div className="rounded-3xl border border-border/70 bg-background/60 p-5">
                            <Badge variant="outline" className="border-primary/30 text-primary">
                                Shell
                            </Badge>
                            <p className="mt-3 text-lg font-semibold text-foreground">
                                Collapsible sidebar with route-aware breadcrumbs and health context.
                            </p>
                        </div>
                        <div className="rounded-3xl border border-border/70 bg-background/60 p-5">
                            <Badge variant="outline" className="border-primary/30 text-primary">
                                Control
                            </Badge>
                            <p className="mt-3 text-lg font-semibold text-foreground">
                                Dense cards and status surfaces tuned for live network operations.
                            </p>
                        </div>
                        <div className="rounded-3xl border border-border/70 bg-background/60 p-5">
                            <Badge variant="outline" className="border-primary/30 text-primary">
                                Validation
                            </Badge>
                            <p className="mt-3 text-lg font-semibold text-foreground">
                                API responses and rewritten forms are now landing on typed, validated paths.
                            </p>
                        </div>
                    </div>
                </section>

                <section className="relative flex items-center justify-center">
                    <div className="w-full max-w-md">
                        <div className="mb-6 flex items-center gap-3 lg:hidden">
                            <img src="/static/logo.webp" alt="OgCloud" className="h-11 w-11 rounded-2xl" />
                            <div>
                                <p className="text-sm font-semibold uppercase tracking-[0.24em] text-primary">
                                    OgCloud
                                </p>
                                <p className="text-sm text-muted-foreground">
                                    Kubernetes-native dashboard
                                </p>
                            </div>
                        </div>

                        <Card className="border-border/70 bg-card/82 shadow-2xl shadow-black/15 backdrop-blur">
                            <CardHeader className="space-y-3">
                                <Badge variant="outline" className="w-fit border-primary/30 text-primary">
                                    Admin Access
                                </Badge>
                                <CardTitle className="text-3xl tracking-tight text-foreground">
                                    Sign in to the dashboard
                                </CardTitle>
                                <CardDescription className="text-sm leading-6">
                                    Use your OgCloud account to access server operations, network
                                    controls, and player management.
                                </CardDescription>
                            </CardHeader>

                            <CardContent>
                                <form onSubmit={handleSubmit} className="space-y-6">
                                    <FieldGroup>
                                        <Field>
                                            <FieldLabel htmlFor="email">Email address</FieldLabel>
                                            <InputGroup>
                                                <InputGroupAddon>
                                                    <MailIcon className="size-4" />
                                                </InputGroupAddon>
                                                <InputGroupInput
                                                    id="email"
                                                    type="email"
                                                    autoComplete="email"
                                                    placeholder="name@company.com"
                                                    aria-invalid={
                                                        form.formState.errors.email ? "true" : "false"
                                                    }
                                                    {...emailField}
                                                />
                                            </InputGroup>
                                            <FieldError errors={[form.formState.errors.email]} />
                                        </Field>

                                        <Field>
                                            <div className="flex items-center justify-between gap-3">
                                                <FieldLabel htmlFor="password">Password</FieldLabel>
                                                <FieldDescription className="text-xs">
                                                    Case-sensitive
                                                </FieldDescription>
                                            </div>
                                            <InputGroup>
                                                <InputGroupAddon>
                                                    <LockKeyholeIcon className="size-4" />
                                                </InputGroupAddon>
                                                <InputGroupInput
                                                    id="password"
                                                    type={showPassword ? "text" : "password"}
                                                    autoComplete="current-password"
                                                    placeholder="Enter your password"
                                                    aria-invalid={
                                                        form.formState.errors.password ? "true" : "false"
                                                    }
                                                    {...passwordField}
                                                />
                                                <InputGroupAddon align="inline-end">
                                                    <InputGroupButton
                                                        type="button"
                                                        size="icon-xs"
                                                        onClick={() =>
                                                            setShowPassword((currentValue) => !currentValue)
                                                        }
                                                        aria-label={
                                                            showPassword
                                                                ? "Hide password"
                                                                : "Show password"
                                                        }
                                                    >
                                                        {showPassword ? (
                                                            <EyeOffIcon className="size-4" />
                                                        ) : (
                                                            <EyeIcon className="size-4" />
                                                        )}
                                                    </InputGroupButton>
                                                </InputGroupAddon>
                                            </InputGroup>
                                            <FieldError errors={[form.formState.errors.password]} />
                                        </Field>
                                    </FieldGroup>

                                    <FieldError>{form.formState.errors.root?.message}</FieldError>

                                    <Button
                                        type="submit"
                                        size="lg"
                                        className="w-full"
                                        disabled={isSubmitting}
                                    >
                                        {isSubmitting ? (
                                            <>
                                                <Loader2Icon className="size-4 animate-spin" />
                                                Signing in
                                            </>
                                        ) : (
                                            "Sign in"
                                        )}
                                    </Button>
                                </form>

                                <div className="mt-6 rounded-2xl border border-border/70 bg-background/55 px-4 py-3 text-sm text-muted-foreground">
                                    OAuth providers are not wired into this dashboard yet. Use your
                                    email/password credentials for now.
                                </div>
                            </CardContent>
                        </Card>
                    </div>
                </section>
            </div>
        </div>
    );
};

export default LoginPage;
