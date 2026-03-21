import { startTransition, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import { zodResolver } from "@hookform/resolvers/zod";
import { EyeIcon, EyeOffIcon, Loader2Icon, LockKeyholeIcon, MailIcon } from "lucide-react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupButton,
    InputGroupInput,
} from "@/components/ui/input-group";
import { loginFormSchema, type LoginFormValues } from "@/features/auth/schemas";
import { useAuthStore } from "@/store/auth-store";

const LoginPage = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);
    const status = useAuthStore((state) => state.status);
    const [showPassword, setShowPassword] = useState(false);

    const form = useForm<LoginFormValues>({
        resolver: zodResolver(loginFormSchema),
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
        <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-background px-4 py-10">
            <div className="pointer-events-none absolute inset-0" aria-hidden="true">
                <div className="absolute inset-0 bg-[radial-gradient(circle_at_top,rgba(37,99,235,0.18),transparent_42%),linear-gradient(180deg,rgba(15,23,42,0.96)_0%,rgba(2,6,23,1)_100%)]" />
                <div className="absolute left-1/2 top-[-12rem] h-[26rem] w-[26rem] -translate-x-1/2 rounded-full bg-blue-500/12 blur-3xl" />
                <div className="absolute bottom-[-10rem] left-[-6rem] h-[22rem] w-[22rem] rounded-full bg-sky-500/10 blur-3xl" />
                <div className="absolute bottom-[-12rem] right-[-4rem] h-[24rem] w-[24rem] rounded-full bg-cyan-400/8 blur-3xl" />
            </div>

            <section className="relative w-full max-w-md">
                <Card className="border-border/70 bg-card/84 shadow-2xl shadow-black/20 backdrop-blur-xl">
                    <CardHeader className="space-y-5 text-center">
                        <img
                            src="/static/logo.webp"
                            alt="OgCloud"
                            className="mx-auto h-auto w-32 max-w-full object-contain"
                        />
                        <div className="space-y-2">
                            <p className="text-xs font-semibold uppercase tracking-[0.28em] text-primary">
                                OgCloud
                            </p>
                            <CardTitle className="text-3xl tracking-tight text-foreground">
                                Sign in to the dashboard
                            </CardTitle>
                            <CardDescription className="text-sm leading-6">
                                Use your OgCloud account to access server operations, network
                                controls, and player management.
                            </CardDescription>
                        </div>
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
                                                    showPassword ? "Hide password" : "Show password"
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

                        <div className="mt-6 h-px w-full bg-border/60" aria-hidden="true" />

                        <div className="mt-4 rounded-2xl border border-border/70 bg-background/55 px-4 py-3 text-sm text-muted-foreground">
                            OAuth providers are not wired into this dashboard yet. Use your
                            email/password credentials for now.
                        </div>
                    </CardContent>
                </Card>
            </section>
        </div>
    );
};

export default LoginPage;
