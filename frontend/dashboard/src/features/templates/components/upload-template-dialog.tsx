import { zodResolver } from "@hookform/resolvers/zod";
import { CheckIcon, LoaderCircleIcon, UploadIcon } from "lucide-react";
import { useEffect, useMemo, useRef } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";

import { Button } from "@/components/ui/button";
import {
    Combobox,
    ComboboxContent,
    ComboboxEmpty,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
} from "@/components/ui/combobox";
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
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
    templateUploadFormSchema,
    type TemplateUploadFormValues,
} from "@/features/templates/schemas";

interface UploadTemplateDialogProps {
    groupSuggestions: string[];
    isSubmitting: boolean;
    onOpenChange: (open: boolean) => void;
    onSubmit: (values: TemplateUploadFormValues) => Promise<void>;
    open: boolean;
}

const UploadTemplateDialog = ({
    groupSuggestions,
    isSubmitting,
    onOpenChange,
    onSubmit,
    open,
}: UploadTemplateDialogProps) => {
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const form = useForm<TemplateUploadFormValues>({
        resolver: zodResolver(templateUploadFormSchema),
        defaultValues: {
            group: "",
            version: "",
        },
    });
    const selectedFile = useWatch({
        control: form.control,
        name: "file",
    });
    const groupInputValue =
        useWatch({
            control: form.control,
            name: "group",
        }) ?? "";

    const normalizedSuggestions = useMemo(
        () => [...new Set(groupSuggestions.map((group) => group.trim()).filter(Boolean))].sort(),
        [groupSuggestions],
    );
    const trimmedGroupInput = groupInputValue.trim();
    const canCreateGroup =
        trimmedGroupInput.length > 0 &&
        !normalizedSuggestions.some(
            (group) => group.toLowerCase() === trimmedGroupInput.toLowerCase(),
        );

    useEffect(() => {
        if (!open) {
            form.reset({
                group: "",
                version: "",
            });
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
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
            <DialogContent className="sm:max-w-2xl">
                <DialogHeader>
                    <DialogTitle>Upload template</DialogTitle>
                    <DialogDescription>
                        Publish a new `template.tar.gz` archive for a server group. Select an
                        existing group or type a new one directly into the combobox.
                    </DialogDescription>
                </DialogHeader>

                <form
                    className="space-y-4"
                    onSubmit={form.handleSubmit(async (values) => {
                        await onSubmit(values);
                    })}
                >
                    <FieldGroup className="grid gap-4 md:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="template-upload-group">Target group</FieldLabel>
                            <Controller
                                control={form.control}
                                name="group"
                                render={({ field }) => (
                                    <Combobox
                                        items={normalizedSuggestions}
                                        value={field.value || null}
                                        inputValue={groupInputValue}
                                        onInputValueChange={(value) => {
                                            field.onChange(value);
                                        }}
                                        onValueChange={(value) => {
                                            field.onChange(value ?? "");
                                        }}
                                        itemToStringValue={(item) => item}
                                    >
                                        <ComboboxInput
                                            id="template-upload-group"
                                            disabled={isSubmitting}
                                            placeholder="Choose or type group id"
                                        />
                                        <ComboboxContent>
                                            <ComboboxList>
                                                {canCreateGroup ? (
                                                    <ComboboxItem value={trimmedGroupInput}>
                                                        <CheckIcon className="size-4 text-primary" />
                                                        Use new group "{trimmedGroupInput}"
                                                    </ComboboxItem>
                                                ) : null}
                                                {normalizedSuggestions.map((group) => (
                                                    <ComboboxItem key={group} value={group}>
                                                        {group}
                                                    </ComboboxItem>
                                                ))}
                                                <ComboboxEmpty>
                                                    No existing groups match this value.
                                                </ComboboxEmpty>
                                            </ComboboxList>
                                        </ComboboxContent>
                                    </Combobox>
                                )}
                            />
                            <FieldDescription>
                                Type a new group id if the target namespace does not exist yet.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.group]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="template-upload-version">Version</FieldLabel>
                            <Input
                                id="template-upload-version"
                                aria-invalid={form.formState.errors.version ? "true" : "false"}
                                disabled={isSubmitting}
                                placeholder="1.0.0"
                                {...form.register("version")}
                            />
                            <FieldDescription>
                                Version tag stored and referenced by server group configurations.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.version]} />
                        </Field>
                    </FieldGroup>

                    <Field>
                        <FieldLabel htmlFor="template-upload-file">Template archive</FieldLabel>
                        <input
                            ref={fileInputRef}
                            id="template-upload-file"
                            type="file"
                            accept=".gz,.tar.gz,application/gzip,application/x-gzip"
                            className="hidden"
                            onChange={(event) => {
                                form.setValue("file", event.target.files?.[0] as File, {
                                    shouldDirty: true,
                                    shouldTouch: true,
                                    shouldValidate: true,
                                });
                            }}
                        />
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                            <Button
                                type="button"
                                variant="outline"
                                disabled={isSubmitting}
                                onClick={() => fileInputRef.current?.click()}
                            >
                                <UploadIcon className="size-4" />
                                Choose file
                            </Button>
                            <div className="min-h-12 flex-1 rounded-xl border border-border/70 bg-background/40 px-4 py-3 text-sm text-muted-foreground">
                                {selectedFile
                                    ? `${selectedFile.name} (${Math.max(
                                          1,
                                          Math.round(selectedFile.size / 1024),
                                      )} KB)`
                                    : "No file selected"}
                            </div>
                        </div>
                        <FieldDescription>
                            Upload the compressed `template.tar.gz` package that should be stored
                            for this group and version.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.file]} />
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
                                <UploadIcon className="size-4" />
                            )}
                            Upload template
                        </Button>
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default UploadTemplateDialog;
