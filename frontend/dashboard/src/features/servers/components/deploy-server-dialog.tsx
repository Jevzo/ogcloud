import { useEffect } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircleIcon, RocketIcon, ServerIcon } from "lucide-react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";

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
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
    InputGroupText,
} from "@/components/ui/input-group";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { deployServerFormSchema, type DeployServerFormValues } from "@/features/servers/schemas";
import { useAccessToken } from "@/features/auth/hooks/use-access-token";
import { requestServerForGroup } from "@/api";
import { getRuntimeProfileLabel } from "@/features/groups/lib/group-runtime";
import type { GroupRecord } from "@/types/group";

interface DeployServerDialogProps {
    groups: GroupRecord[];
    onCompleted?: () => Promise<void> | void;
    onOpenChange: (open: boolean) => void;
    open: boolean;
}

const DeployServerDialog = ({
    groups,
    onCompleted,
    onOpenChange,
    open,
}: DeployServerDialogProps) => {
    const getAccessToken = useAccessToken();
    const form = useForm<DeployServerFormValues>({
        resolver: zodResolver(deployServerFormSchema),
        defaultValues: {
            count: 1,
            groupId: groups[0]?.id ?? "",
        },
    });
    const selectedGroupId = useWatch({
        control: form.control,
        name: "groupId",
    });

    useEffect(() => {
        if (!groups.length) {
            form.reset({
                count: form.getValues("count") || 1,
                groupId: "",
            });
            return;
        }

        const selectedGroupId = form.getValues("groupId");

        if (!groups.some((group) => group.id === selectedGroupId)) {
            form.setValue("groupId", groups[0].id, {
                shouldDirty: false,
                shouldTouch: false,
                shouldValidate: false,
            });
        }
    }, [form, groups]);

    const selectedGroup = groups.find((group) => group.id === selectedGroupId) ?? groups[0] ?? null;

    const handleSubmit = form.handleSubmit(async (values) => {
        try {
            const accessToken = await getAccessToken();
            const createdRequests = await requestServerForGroup(
                accessToken,
                values.groupId,
                values.count,
            );

            await onCompleted?.();
            toast.success(
                `Requested ${createdRequests.length} new ${values.groupId} instance${createdRequests.length === 1 ? "" : "s"}.`,
            );
            onOpenChange(false);
            form.reset({
                count: 1,
                groupId: values.groupId,
            });
        } catch (error) {
            const message =
                error instanceof Error ? error.message : "Unable to request a new instance.";

            form.setError("root", { message });
            toast.error(message);
        }
    });

    const isSubmitting = form.formState.isSubmitting;

    return (
        <Dialog
            open={open}
            onOpenChange={(nextOpen) => {
                if (isSubmitting) {
                    return;
                }

                onOpenChange(nextOpen);
            }}
        >
            <DialogContent className="sm:max-w-xl">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <RocketIcon className="size-4 text-primary" />
                        Deploy new server instances
                    </DialogTitle>
                    <DialogDescription>
                        Request one or more new runtime instances for a specific server group.
                    </DialogDescription>
                </DialogHeader>

                <form onSubmit={handleSubmit} className="space-y-6">
                    <FieldGroup>
                        <Field>
                            <FieldLabel htmlFor="deploy-group">Target group</FieldLabel>
                            <Controller
                                control={form.control}
                                name="groupId"
                                render={({ field }) => (
                                    <Select
                                        value={field.value}
                                        onValueChange={(value) => {
                                            field.onChange(value);
                                            form.clearErrors("root");
                                        }}
                                        disabled={isSubmitting || groups.length === 0}
                                    >
                                        <SelectTrigger id="deploy-group" className="w-full">
                                            <SelectValue
                                                placeholder={
                                                    groups.length === 0
                                                        ? "No groups available"
                                                        : "Select a group"
                                                }
                                            />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {groups.map((group) => (
                                                <SelectItem key={group.id} value={group.id}>
                                                    {group.id}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                )}
                            />
                            <FieldDescription>
                                Choose the group that should receive the requested instances.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.groupId]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="deploy-count">Instance count</FieldLabel>
                            <InputGroup>
                                <InputGroupAddon>
                                    <ServerIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    id="deploy-count"
                                    type="number"
                                    min={1}
                                    max={25}
                                    step={1}
                                    aria-invalid={form.formState.errors.count ? "true" : "false"}
                                    {...form.register("count", {
                                        valueAsNumber: true,
                                        onChange: () => form.clearErrors("root"),
                                    })}
                                />
                                <InputGroupAddon align="inline-end">
                                    <InputGroupText>max 25</InputGroupText>
                                </InputGroupAddon>
                            </InputGroup>
                            <FieldDescription>
                                Request multiple instances in one action when you already know the
                                target scale-up.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.count]} />
                        </Field>
                    </FieldGroup>

                    {selectedGroup ? (
                        <div className="grid gap-3 rounded-xl border border-border/70 bg-muted/35 p-4 md:grid-cols-3">
                            <div>
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Group type
                                </div>
                                <div className="mt-1 text-sm font-medium text-foreground">
                                    {selectedGroup.type}
                                </div>
                            </div>
                            <div>
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Runtime
                                </div>
                                <div className="mt-1 text-sm font-medium text-foreground">
                                    {getRuntimeProfileLabel(selectedGroup.runtimeProfile)}
                                </div>
                            </div>
                            <div>
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Maintenance
                                </div>
                                <div className="mt-1 text-sm font-medium text-foreground">
                                    {selectedGroup.maintenance ? "Enabled" : "Disabled"}
                                </div>
                            </div>
                        </div>
                    ) : (
                        <div className="rounded-xl border border-dashed border-border/80 bg-muted/20 px-4 py-6 text-sm text-muted-foreground">
                            No deployable groups are currently available.
                        </div>
                    )}

                    <FieldError>{form.formState.errors.root?.message}</FieldError>

                    <DialogFooter>
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => onOpenChange(false)}
                            disabled={isSubmitting}
                        >
                            Cancel
                        </Button>
                        <Button type="submit" disabled={isSubmitting || groups.length === 0}>
                            {isSubmitting ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Requesting
                                </>
                            ) : (
                                <>
                                    <RocketIcon className="size-4" />
                                    Request instances
                                </>
                            )}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default DeployServerDialog;
