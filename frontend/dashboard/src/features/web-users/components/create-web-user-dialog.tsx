import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircleIcon, UserPlusIcon } from "lucide-react";
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
import {
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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
    const form = useForm<CreateWebUserFormValues>({
        resolver: zodResolver(createWebUserFormSchema),
        defaultValues: {
            email: "",
            password: "",
            role: "DEVELOPER",
        },
    });

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
            <DialogContent className="sm:max-w-xl">
                <DialogHeader>
                    <DialogTitle>Create web user</DialogTitle>
                    <DialogDescription>
                        Provision a dashboard account and assign the initial access role.
                    </DialogDescription>
                </DialogHeader>

                <form
                    className="space-y-4"
                    onSubmit={form.handleSubmit(async (values) => {
                        await onSubmit(values);
                    })}
                >
                    <Field>
                        <FieldLabel htmlFor="create-web-user-email">Email</FieldLabel>
                        <Input
                            id="create-web-user-email"
                            type="email"
                            aria-invalid={form.formState.errors.email ? "true" : "false"}
                            disabled={isSubmitting}
                            placeholder="user@ogcloud.local"
                            {...form.register("email")}
                        />
                        <FieldDescription>
                            This email is used for sign-in and password reset flows.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.email]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="create-web-user-password">Password</FieldLabel>
                        <Input
                            id="create-web-user-password"
                            type="password"
                            aria-invalid={form.formState.errors.password ? "true" : "false"}
                            disabled={isSubmitting}
                            placeholder="Set an initial password"
                            {...form.register("password")}
                        />
                        <FieldDescription>
                            The user can rotate this password after the first login.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.password]} />
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
                                <UserPlusIcon className="size-4" />
                            )}
                            Create user
                        </Button>
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default CreateWebUserDialog;
