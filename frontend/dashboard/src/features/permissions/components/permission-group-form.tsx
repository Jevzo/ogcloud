import { type ReactNode } from "react";
import { Controller, useWatch, type UseFormReturn } from "react-hook-form";

import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { Badge } from "@/components/ui/badge";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import type { PermissionGroupFormValues } from "@/types/permission";

const SECTION_CLASS_NAME = "space-y-4 rounded-xl border border-border/70 bg-muted/25 p-4";

const Section = ({
    children,
    description,
    title,
}: {
    children: ReactNode;
    description: string;
    title: string;
}) => (
    <section className={SECTION_CLASS_NAME}>
        <div className="space-y-1">
            <h3 className="text-sm font-semibold text-foreground">{title}</h3>
            <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        {children}
    </section>
);

interface PermissionGroupFormProps {
    disableIdentityFields?: boolean;
    form: UseFormReturn<PermissionGroupFormValues>;
}

const PermissionGroupForm = ({
    disableIdentityFields = false,
    form,
}: PermissionGroupFormProps) => {
    const id = useWatch({ control: form.control, name: "id" }) ?? form.getValues("id");
    const name = useWatch({ control: form.control, name: "name" }) ?? form.getValues("name");
    const chatPrefix =
        useWatch({ control: form.control, name: "display.chatPrefix" }) ??
        form.getValues("display.chatPrefix");
    const chatSuffix =
        useWatch({ control: form.control, name: "display.chatSuffix" }) ??
        form.getValues("display.chatSuffix");
    const nameColor =
        useWatch({ control: form.control, name: "display.nameColor" }) ??
        form.getValues("display.nameColor");
    const tabPrefix =
        useWatch({ control: form.control, name: "display.tabPrefix" }) ??
        form.getValues("display.tabPrefix");
    const previewGroupName = name.trim() || id.trim() || "Group";
    const previewNameColor = nameColor.trim() || "&7";
    const previewChatPrefix = chatPrefix.trim() || "&7[Group] ";
    const previewChatSuffix = chatSuffix.trim() || "&7: &f";
    const previewTabPrefix = tabPrefix.trim() || "&7";
    const displayPreviewFallbacks = {
        chatPrefix: `${previewChatPrefix}${previewNameColor}${previewGroupName}`,
        chatSuffix: `${previewNameColor}${previewGroupName}${previewChatSuffix}Hello`,
        nameColor: `${previewNameColor}${previewGroupName}`,
        tabPrefix: `${previewTabPrefix}${previewNameColor}${previewGroupName}`,
    } as const;

    return (
        <div className="space-y-4">
            <Section
                title="Core settings"
                description="Identity, precedence, and default fallback behavior for this permission group."
            >
                <FieldGroup className="grid gap-4 md:grid-cols-2">
                    <Field>
                        <FieldLabel htmlFor="permission-group-id">Group ID</FieldLabel>
                        <Input
                            id="permission-group-id"
                            disabled={disableIdentityFields}
                            aria-invalid={form.formState.errors.id ? "true" : "false"}
                            {...form.register("id")}
                        />
                        <FieldDescription>
                            Unique identifier used in permission assignments and player records.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.id]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-name">Display name</FieldLabel>
                        <Input
                            id="permission-group-name"
                            aria-invalid={form.formState.errors.name ? "true" : "false"}
                            {...form.register("name")}
                        />
                        <FieldDescription>
                            Friendly label shown across dashboard and moderation tools.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.name]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-weight">Weight</FieldLabel>
                        <Input
                            id="permission-group-weight"
                            type="number"
                            step={1}
                            aria-invalid={form.formState.errors.weight ? "true" : "false"}
                            {...form.register("weight")}
                        />
                        <FieldDescription>
                            Lower values sort first in the current dashboard ordering.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.weight]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-default">Default group</FieldLabel>
                        <Controller
                            control={form.control}
                            name="default"
                            render={({ field }) => (
                                <div className="flex h-8 items-center justify-between rounded-lg border border-border bg-background px-2.5">
                                    <div className="flex items-center gap-2 text-sm text-foreground">
                                        <span>{field.value ? "Enabled" : "Disabled"}</span>
                                        {field.value ? (
                                            <Badge
                                                variant="outline"
                                                className="border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                            >
                                                Fallback rank
                                            </Badge>
                                        ) : null}
                                    </div>
                                    <Switch
                                        id="permission-group-default"
                                        checked={field.value}
                                        onCheckedChange={field.onChange}
                                    />
                                </div>
                            )}
                        />
                        <FieldDescription>
                            Players without an explicit assignment fall back to this group.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.default]} />
                    </Field>
                </FieldGroup>
            </Section>

            <Section
                title="Display formatting"
                description="Minecraft formatting values applied to chat, tab list, and identity rendering."
            >
                <FieldGroup className="grid gap-4 md:grid-cols-2">
                    <Field>
                        <FieldLabel htmlFor="permission-group-chat-prefix">Chat prefix</FieldLabel>
                        <Input
                            id="permission-group-chat-prefix"
                            {...form.register("display.chatPrefix")}
                        />
                        <FieldDescription>
                            Text shown before player names in chat.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.display?.chatPrefix]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-chat-suffix">Chat suffix</FieldLabel>
                        <Input
                            id="permission-group-chat-suffix"
                            {...form.register("display.chatSuffix")}
                        />
                        <FieldDescription>
                            Text appended after player names in chat.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.display?.chatSuffix]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-name-color">Name color</FieldLabel>
                        <Input
                            id="permission-group-name-color"
                            {...form.register("display.nameColor")}
                        />
                        <FieldDescription>
                            Color code applied to player names.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.display?.nameColor]} />
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-tab-prefix">Tab prefix</FieldLabel>
                        <Input
                            id="permission-group-tab-prefix"
                            {...form.register("display.tabPrefix")}
                        />
                        <FieldDescription>
                            Prefix displayed in the tab list.
                        </FieldDescription>
                        <FieldError errors={[form.formState.errors.display?.tabPrefix]} />
                    </Field>
                </FieldGroup>
            </Section>

            <Section
                title="Preview"
                description="Live Minecraft-format previews based on the values currently in the form."
            >
                <div className="grid gap-4 md:grid-cols-2">
                    <div className="space-y-2 rounded-xl border border-border/70 bg-background/45 p-4">
                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                            Chat prefix
                        </div>
                        <MinecraftTextPreview
                            value={chatPrefix}
                            fallback={displayPreviewFallbacks.chatPrefix}
                            emptyFallback="Not set"
                            useFallbackForFormatOnly
                            className="font-mono"
                        />
                    </div>

                    <div className="space-y-2 rounded-xl border border-border/70 bg-background/45 p-4">
                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                            Chat suffix
                        </div>
                        <MinecraftTextPreview
                            value={chatSuffix}
                            fallback={displayPreviewFallbacks.chatSuffix}
                            emptyFallback="Not set"
                            useFallbackForFormatOnly
                            className="font-mono"
                        />
                    </div>

                    <div className="space-y-2 rounded-xl border border-border/70 bg-background/45 p-4">
                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                            Name color
                        </div>
                        <MinecraftTextPreview
                            value={nameColor}
                            fallback={displayPreviewFallbacks.nameColor}
                            emptyFallback="Not set"
                            useFallbackForFormatOnly
                            className="font-mono"
                        />
                    </div>

                    <div className="space-y-2 rounded-xl border border-border/70 bg-background/45 p-4">
                        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                            Tab prefix
                        </div>
                        <MinecraftTextPreview
                            value={tabPrefix}
                            fallback={displayPreviewFallbacks.tabPrefix}
                            emptyFallback="Not set"
                            useFallbackForFormatOnly
                            className="font-mono"
                        />
                    </div>
                </div>
            </Section>
        </div>
    );
};

export default PermissionGroupForm;
