import { zodResolver } from "@hookform/resolvers/zod";
import {
    CheckIcon,
    FileArchiveIcon,
    FolderTreeIcon,
    LoaderCircleIcon,
    TagIcon,
    UploadIcon,
} from "lucide-react";
import { useMemo, useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";

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
import { TypeaheadSelect } from "@/components/ui/typeahead-select";
import {
    templateUploadFormSchema,
    type TemplateUploadFormValues,
} from "@/features/templates/schemas";
import { cn } from "@/lib/utils";

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
    const initialFocusRef = useRef<HTMLDivElement | null>(null);
    const [isDragActive, setIsDragActive] = useState(false);
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
    const versionInputValue =
        useWatch({
            control: form.control,
            name: "version",
        }) ?? "";

    const normalizedSuggestions = useMemo(
        () => [...new Set(groupSuggestions.map((group) => group.trim()).filter(Boolean))].sort(),
        [groupSuggestions],
    );
    const trimmedGroupInput = groupInputValue.trim();

    const setSelectedFile = (file: File | null | undefined) => {
        form.setValue("file", file as File, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
    };

    const setTargetGroup = (group: string) => {
        form.setValue("group", group, {
            shouldDirty: true,
            shouldTouch: true,
            shouldValidate: true,
        });
    };

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
                className="max-h-[92vh] overflow-y-auto sm:max-w-2xl"
                onOpenAutoFocus={(event) => {
                    event.preventDefault();
                    initialFocusRef.current?.focus();
                }}
            >
                <div ref={initialFocusRef} tabIndex={-1} className="outline-none">
                    <DialogHeader>
                        <DialogTitle className="flex items-center gap-2">
                            <UploadIcon className="size-4 text-primary" />
                            Upload template
                        </DialogTitle>
                        <DialogDescription>
                            Publish a new `template.tar.gz` archive for a server group. Select an
                            existing group or type a new one directly into the input.
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
                                Target group
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {trimmedGroupInput || "Select or type a group"}
                            </div>
                        </div>
                        <div>
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Version tag
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {versionInputValue.trim() || "Set a version"}
                            </div>
                        </div>
                        <div>
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Archive
                            </div>
                            <div className="mt-1 text-sm font-medium text-foreground">
                                {selectedFile ? "Ready to upload" : "No file selected"}
                            </div>
                        </div>
                    </div>

                    <FieldGroup className="grid gap-4 md:grid-cols-2">
                        <Field>
                            <FieldLabel htmlFor="template-upload-group">Target group</FieldLabel>
                            <TypeaheadSelect
                                createOptionIcon={
                                    <CheckIcon className="size-4 shrink-0 text-primary" />
                                }
                                createOptionLabel={(query) =>
                                    query.trim().length > 0 ? `Use "${query.trim()}" instead` : null
                                }
                                disabled={isSubmitting}
                                emptyMessage="No groups available."
                                id="template-upload-group"
                                inputIcon={<FolderTreeIcon className="size-4" />}
                                invalid={Boolean(form.formState.errors.group)}
                                onValueChange={setTargetGroup}
                                options={normalizedSuggestions}
                                placeholder="Type an existing or new group id"
                                renderOptionIcon={() => (
                                    <FolderTreeIcon className="size-4 shrink-0 text-muted-foreground" />
                                )}
                                value={groupInputValue}
                            />

                            <FieldDescription>
                                Click into the field to browse known groups, or type a new group id
                                directly.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.group]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="template-upload-version">Version</FieldLabel>
                            <InputGroup>
                                <InputGroupAddon>
                                    <TagIcon className="size-4" />
                                </InputGroupAddon>
                                <InputGroupInput
                                    id="template-upload-version"
                                    aria-invalid={form.formState.errors.version ? "true" : "false"}
                                    disabled={isSubmitting}
                                    placeholder="1.0.0"
                                    {...form.register("version")}
                                />
                            </InputGroup>
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
                                setSelectedFile(event.target.files?.[0]);
                            }}
                        />

                        <div
                            role="button"
                            tabIndex={isSubmitting ? -1 : 0}
                            className={cn(
                                "rounded-xl border border-dashed p-5 transition-colors outline-none",
                                "border-border/80 bg-background/40",
                                !isSubmitting &&
                                    "cursor-pointer hover:border-primary/25 hover:bg-primary/5",
                                isDragActive && "border-primary/35 bg-primary/5",
                            )}
                            onClick={() => {
                                if (!isSubmitting) {
                                    fileInputRef.current?.click();
                                }
                            }}
                            onKeyDown={(event) => {
                                if (isSubmitting) {
                                    return;
                                }

                                if (event.key === "Enter" || event.key === " ") {
                                    event.preventDefault();
                                    fileInputRef.current?.click();
                                }
                            }}
                            onDragEnter={(event) => {
                                event.preventDefault();

                                if (!isSubmitting) {
                                    setIsDragActive(true);
                                }
                            }}
                            onDragOver={(event) => {
                                event.preventDefault();

                                if (!isSubmitting) {
                                    setIsDragActive(true);
                                }
                            }}
                            onDragLeave={(event) => {
                                event.preventDefault();
                                setIsDragActive(false);
                            }}
                            onDrop={(event) => {
                                event.preventDefault();
                                setIsDragActive(false);

                                if (isSubmitting) {
                                    return;
                                }

                                setSelectedFile(event.dataTransfer.files?.[0]);
                            }}
                        >
                            {selectedFile ? (
                                <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_120px]">
                                    <div className="min-w-0">
                                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                            Selected archive
                                        </div>
                                        <div className="mt-1 flex min-w-0 items-center gap-2 text-sm font-medium text-foreground">
                                            <FileArchiveIcon className="size-4 shrink-0 text-primary" />
                                            <span className="min-w-0 break-all">
                                                {selectedFile.name}
                                            </span>
                                        </div>
                                        <div className="mt-2 text-sm leading-6 text-muted-foreground">
                                            Click or drop another archive here to replace it.
                                        </div>
                                    </div>
                                    <div>
                                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                            File size
                                        </div>
                                        <div className="mt-1 text-sm font-medium text-foreground">
                                            {Math.max(1, Math.round(selectedFile.size / 1024))} KB
                                        </div>
                                    </div>
                                </div>
                            ) : (
                                <div className="flex min-w-0 items-start gap-3">
                                    <div className="flex size-10 shrink-0 items-center justify-center rounded-full border border-primary/20 bg-primary/10 text-primary">
                                        <UploadIcon className="size-4" />
                                    </div>
                                    <div className="min-w-0">
                                        <div className="text-sm font-medium text-foreground">
                                            Drop `template.tar.gz` here or click to browse
                                        </div>
                                        <div className="mt-1 text-sm leading-6 text-muted-foreground">
                                            Compressed template archives only. The selected file
                                            will be uploaded for the chosen group and version.
                                        </div>
                                    </div>
                                </div>
                            )}
                        </div>

                        <FieldDescription>
                            Upload the compressed `template.tar.gz` package that should be stored
                            for this group and version.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.file]} />
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
                                <UploadIcon className="size-4" />
                            )}
                            Upload template
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
};

export default UploadTemplateDialog;
