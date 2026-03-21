import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircleIcon, PencilLineIcon } from "lucide-react";
import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
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
    const form = useForm<UpdateWebUserFormValues>({
        resolver: zodResolver(updateWebUserFormSchema),
        defaultValues: {
            email: "",
            password: "",
            role: "DEVELOPER",
            username: "",
        },
    });

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
            <DialogContent className="sm:max-w-xl">
                <DialogHeader>
                    <DialogTitle>Update web user</DialogTitle>
                    <DialogDescription>
                        {targetUser
                            ? `Modify account details for ${targetUser.email}.`
                            : "Modify the selected account."}
                    </DialogDescription>
                </DialogHeader>

                <form
                    className="space-y-4"
                    onSubmit={form.handleSubmit(async (values) => {
                        await onSubmit(values);
                    })}
                >
                    <Field>
                        <FieldLabel htmlFor="edit-web-user-email">Email</FieldLabel>
                        <Input
                            id="edit-web-user-email"
                            type="email"
                            aria-invalid={form.formState.errors.email ? "true" : "false"}
                            disabled={isSubmitting}
                            {...form.register("email")}
                        />
                        <FieldDescription>
                            Updating the email changes the account's login identifier.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.email]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="edit-web-user-username">Username</FieldLabel>
                        <Input
                            id="edit-web-user-username"
                            type="text"
                            aria-invalid={form.formState.errors.username ? "true" : "false"}
                            disabled={isSubmitting}
                            {...form.register("username")}
                        />
                        <FieldDescription>
                            Friendly display name used throughout the dashboard.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.username]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="edit-web-user-password">New password</FieldLabel>
                        <Input
                            id="edit-web-user-password"
                            type="password"
                            disabled={isSubmitting}
                            placeholder="Leave blank to keep the current password"
                            {...form.register("password")}
                        />
                        <FieldDescription>
                            Optional. Leave blank to preserve the current password.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.password]} />
                    </Field>

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

                    <div className="flex justify-end gap-3">
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
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default EditWebUserDialog;
