import { zodResolver } from "@hookform/resolvers/zod";
import {
    KeyRoundIcon,
    LoaderCircleIcon,
    MailIcon,
    PencilLineIcon,
    ShieldIcon,
    UserIcon,
} from "lucide-react";
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
    updateWebUserFormSchema,
    type UpdateWebUserFormValues,
} from "@/features/web-users/schemas";
import { WEB_USER_ROLES, type WebUserRecord } from "@/types/web-user";

interface EditWebUserDialogProps {
    isSubmitting: boolean;
    onOpenChange: (open: boolean) => void;
    onSubmit: (values: UpdateWebUserFormValues) => Promise<void>;
    open: boolean;
    targetUser: WebUserRecord | null;
}

const EditWebUserDialog = ({
    isSubmitting,
    onOpenChange,
    onSubmit,
    open,
    targetUser,
}: EditWebUserDialogProps) => {
    const initialFocusRef = useRef<HTMLDivElement | null>(null);
    const form = useForm<UpdateWebUserFormValues>({
        resolver: zodResolver(updateWebUserFormSchema),
        defaultValues: {
            email: "",
            password: "",
            role: "DEVELOPER",
            username: "",
        },
    });
    const emailValue =
        useWatch({
            control: form.control,
            name: "email",
        }) ?? "";
    const usernameValue =
        useWatch({
            control: form.control,
            name: "username",
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
        if (!open || !targetUser) {
            return;
        }

        form.reset({
            email: targetUser.email,
            password: "",
            role: targetUser.role,
            username: targetUser.username,
        });
    }, [form, open, targetUser]);

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
                            <PencilLineIcon className="size-4 text-primary" />
                            Update web user
                        </DialogTitle>
                        <DialogDescription>
                            {targetUser
                                ? `Modify account details for ${targetUser.email}.`
                                : "Modify the selected account."}
                        </DialogDescription>
                    </DialogHeader>
                </div>

                <form
                    className="space-y-6"
                    onSubmit={form.handleSubmit(async (values) => {
                        await onSubmit(values);
                    })}
                >
                    <div className="grid gap-3 rounded-xl border border-border/70 bg-muted/35 p-4 md:grid-cols-2">
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
                                Username
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {usernameValue.trim() || "Set display name"}
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
                                {passwordValue.trim() ? "Will be updated" : "Keep current password"}
                            </div>
                        </div>
                    </div>

                    <FieldGroup className="grid gap-4 md:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="edit-web-user-email">Email</FieldLabel>
                            <InputGroup>
                                <InputGroupAddon>
                                    <MailIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    id="edit-web-user-email"
                                    type="email"
                                    aria-invalid={form.formState.errors.email ? "true" : "false"}
                                    disabled={isSubmitting}
                                    placeholder="user@ogcloud.local"
                                    {...form.register("email")}
                                />
                            </InputGroup>
                            <FieldDescription>
                                Updating the email changes the account's login identifier.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.email]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="edit-web-user-username">Username</FieldLabel>
                            <InputGroup>
                                <InputGroupAddon>
                                    <UserIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    id="edit-web-user-username"
                                    type="text"
                                    aria-invalid={form.formState.errors.username ? "true" : "false"}
                                    disabled={isSubmitting}
                                    placeholder="dashboard-operator"
                                    {...form.register("username")}
                                />
                            </InputGroup>
                            <FieldDescription>
                                Friendly display name used throughout the dashboard.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.username]} />
                        </Field>
                    </FieldGroup>

                    <FieldGroup className="grid gap-4 md:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="edit-web-user-role">Role</FieldLabel>
                            <Controller
                                control={form.control}
                                name="role"
                                render={({ field }) => (
                                    <Select
                                        value={field.value}
                                        onValueChange={field.onChange}
                                        disabled={isSubmitting}
                                    >
                                        <SelectTrigger id="edit-web-user-role" className="w-full">
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

                        <Field>
                            <FieldLabel htmlFor="edit-web-user-password">New password</FieldLabel>
                            <InputGroup>
                                <InputGroupAddon>
                                    <KeyRoundIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    id="edit-web-user-password"
                                    type="password"
                                    aria-invalid={form.formState.errors.password ? "true" : "false"}
                                    disabled={isSubmitting}
                                    placeholder="Enter a new password"
                                    {...form.register("password")}
                                />
                            </InputGroup>
                            <FieldDescription>
                                Optional. Leave blank to preserve the current password.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.password]} />
                        </Field>
                    </FieldGroup>

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
                                <PencilLineIcon className="size-4" />
                            )}
                            Update user
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default EditWebUserDialog;
