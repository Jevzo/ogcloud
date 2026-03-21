import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRoundIcon, LoaderCircleIcon, MailIcon, ShieldIcon, UserPlusIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";

import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { InputGroup, InputGroupAddon, InputGroupInput } from "@/components/ui/input-group";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import {
    createWebUserFormSchema,
    type CreateWebUserFormValues,
} from "@/features/web-users/schemas";
import { WEB_USER_ROLES } from "@/types/web-user";

interface CreateWebUserDialogProps {
    isSubmitting: boolean;
    onOpenChange: (open: boolean) => void;
    onSubmit: (values: CreateWebUserFormValues) => Promise<void>;
    open: boolean;
}

const CreateWebUserDialog = ({
    isSubmitting,
    onOpenChange,
    onSubmit,
    open,
}: CreateWebUserDialogProps) => {
    const initialFocusRef = useRef<HTMLDivElement | null>(null);
    const form = useForm<CreateWebUserFormValues>({
        resolver: zodResolver(createWebUserFormSchema),
        defaultValues: {
            email: "",
            password: "",
            role: "DEVELOPER",
        },
    });
    const emailValue =
        useWatch({
            control: form.control,
            name: "email",
        }) ?? "";
    const passwordValue =
        useWatch({
            control: form.control,
            name: "password",
        }) ?? "";
    const roleValue =
        useWatch({
            control: form.control,
            name: "role",
        }) ?? "DEVELOPER";

    useEffect(() => {
        if (open) {
            form.reset({
                email: "",
                password: "",
                role: "DEVELOPER",
            });
        }
    }, [form, open]);

    return (
        <Dialog
            open={open}
            onOpenChange={(nextOpen) => {
                if (!isSubmitting) {
                    onOpenChange(nextOpen);
                }
            }}
        >
            <DialogContent
                className="sm:max-w-xl"
                onOpenAutoFocus={(event) => {
                    event.preventDefault();
                    initialFocusRef.current?.focus();
                }}
            >
                <div ref={initialFocusRef} tabIndex={-1} className="outline-none">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <UserPlusIcon className="size-4 text-primary" />
                            Create web user
                        </DialogTitle>
                        <DialogDescription>
                            Provision a dashboard account and assign the initial access role.
                        </DialogDescription>
                    </DialogHeader>
                </div>

                <form
                    className="space-y-6"
                    onSubmit={form.handleSubmit(async (values) => {
                        await onSubmit(values);
                    })}
                >
                    <div className="grid gap-3 rounded-xl border border-border/70 bg-muted/35 p-4 md:grid-cols-3">
                        <div>
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Sign-in email
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {emailValue.trim() || "Set account email"}
                            </div>
                        </div>
                        <div>
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Access role
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {roleValue}
                            </div>
                        </div>
                        <div>
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Password
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {passwordValue.trim()
                                    ? "Ready to provision"
                                    : "Set initial password"}
                            </div>
                        </div>
                    </div>

                    <FieldGroup className="grid gap-4 md:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="create-web-user-email">Email</FieldLabel>
                            <InputGroup>
                                <InputGroupAddon>
                                    <MailIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    id="create-web-user-email"
                                    type="email"
                                    aria-invalid={form.formState.errors.email ? "true" : "false"}
                                    disabled={isSubmitting}
                                    placeholder="user@ogcloud.local"
                                    {...form.register("email")}
                                />
                            </InputGroup>
                            <FieldDescription>
                                Used for sign-in and password reset flows.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.email]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="create-web-user-role">Role</FieldLabel>
                            <Controller
                                control={form.control}
                                name="role"
                                render={({ field }) => (
                                    <Select
                                        value={field.value}
                                        onValueChange={field.onChange}
                                        disabled={isSubmitting}
                                    >
                                        <SelectTrigger id="create-web-user-role" className="w-full">
                                            <ShieldIcon className="size-4 text-muted-foreground" />
                                            <SelectValue placeholder="Select a role" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {WEB_USER_ROLES.map((role) => (
                                                <SelectItem key={role} value={role}>
                                                    {role}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                )}
                            />
                            <FieldDescription>
                                Controls which dashboard actions this account can perform.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.role]} />
                        </Field>
                    </FieldGroup>

                    <Field>
                        <FieldLabel htmlFor="create-web-user-password">Password</FieldLabel>
                        <InputGroup>
                            <InputGroupAddon>
                                <KeyRoundIcon className="size-4" />
                            </InputGroupAddon>
                            <InputGroupInput
                                id="create-web-user-password"
                                type="password"
                                aria-invalid={form.formState.errors.password ? "true" : "false"}
                                disabled={isSubmitting}
                                placeholder="Set an initial password"
                                {...form.register("password")}
                            />
                        </InputGroup>
                        <FieldDescription>
                            The user can rotate this password after the first login.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.password]} />
                    </Field>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            disabled={isSubmitting}
                            onClick={() => onOpenChange(false)}
                        >
                            Cancel
                        </Button>
                        <Button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? (
                                <LoaderCircleIcon className="size-4 animate-spin" />
                            ) : (
                                <UserPlusIcon className="size-4" />
                            )}
                            Create user
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default CreateWebUserDialog;
