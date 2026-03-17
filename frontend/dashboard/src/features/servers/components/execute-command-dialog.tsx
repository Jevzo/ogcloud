import { zodResolver } from "@hookform/resolvers/zod";
import { LoaderCircleIcon, TerminalIcon } from "lucide-react";
import { useForm } from "react-hook-form";
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
import {
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
} from "@/components/ui/field";
import {
    InputGroup,
    InputGroupAddon,
    InputGroupInput,
} from "@/components/ui/input-group";
import {
    executeCommandFormSchema,
    type ExecuteCommandFormValues,
} from "@/features/servers/schemas";
import { useAccessToken } from "@/hooks/use-access-token";
import { executeCommand } from "@/lib/api";
import type { CommandTargetType } from "@/types/command";

interface ExecuteCommandDialogProps {
    description: string;
    onCompleted?: () => Promise<void> | void;
    onOpenChange: (open: boolean) => void;
    open: boolean;
    submitLabel: string;
    target: string;
    targetType: CommandTargetType;
    title: string;
}

const getScopeLabel = (targetType: CommandTargetType) => {
    if (targetType === "server") {
        return "Single server";
    }

    if (targetType === "group") {
        return "Group broadcast";
    }

    return "Network-wide broadcast";
};

const getTargetLabel = (target: string, targetType: CommandTargetType) =>
    targetType === "all" ? "All running servers" : target;

const getSuccessMessage = (target: string, targetType: CommandTargetType) => {
    if (targetType === "server") {
        return `Sent command to ${target}.`;
    }

    if (targetType === "group") {
        return `Sent command to all running servers in ${target}.`;
    }

    return "Sent command to all running servers.";
};

const ExecuteCommandDialog = ({
    description,
    onCompleted,
    onOpenChange,
    open,
    submitLabel,
    target,
    targetType,
    title,
}: ExecuteCommandDialogProps) => {
    const getAccessToken = useAccessToken();
    const form = useForm<ExecuteCommandFormValues>({
        resolver: zodResolver(executeCommandFormSchema),
        defaultValues: {
            command: "",
        },
    });

    const handleSubmit = form.handleSubmit(async (values) => {
        try {
            const accessToken = await getAccessToken();
            await executeCommand(accessToken, {
                command: values.command.trim(),
                target,
                targetType,
            });

            await onCompleted?.();
            toast.success(getSuccessMessage(target, targetType));
            onOpenChange(false);
            form.reset();
        } catch (error) {
            const message = error instanceof Error ? error.message : "Unable to send command.";

            form.setError("root", { message });
            toast.error(message);
        }
    });

    const commandField = form.register("command", {
        onChange: () => form.clearErrors("root"),
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
                        <TerminalIcon className="size-4 text-primary" />
                        {title}
                    </DialogTitle>
                    <DialogDescription>{description}</DialogDescription>
                </DialogHeader>

                <form onSubmit={handleSubmit} className="space-y-6">
                    <div className="rounded-xl border border-border/70 bg-muted/35 p-4">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                            <div>
                                <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                    Target
                                </div>
                                <div className="mt-1 text-sm font-medium text-foreground">
                                    {getTargetLabel(target, targetType)}
                                </div>
                            </div>
                            <div className="rounded-full border border-primary/25 bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
                                {getScopeLabel(targetType)}
                            </div>
                        </div>
                    </div>

                    <Field>
                        <FieldLabel htmlFor="execute-command-input">Command</FieldLabel>
                        <InputGroup>
                            <InputGroupAddon>
                                <TerminalIcon className="size-4" />
                            </InputGroupAddon>
                            <InputGroupInput
                                id="execute-command-input"
                                type="text"
                                autoFocus
                                placeholder="say Server restart in 30 seconds"
                                aria-invalid={form.formState.errors.command ? "true" : "false"}
                                {...commandField}
                            />
                        </InputGroup>
                        <FieldDescription>
                            Enter the Minecraft console command without a leading slash.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.command]} />
                    </Field>

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
                        <Button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Sending
                                </>
                            ) : (
                                <>
                                    <TerminalIcon className="size-4" />
                                    {submitLabel}
                                </>
                            )}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default ExecuteCommandDialog;
