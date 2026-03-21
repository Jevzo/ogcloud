import { type ReactNode } from "react";
import { Controller, useWatch, type UseFormReturn } from "react-hook-form";

import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { useAuthStore } from "@/store/auth-store";
import type { PermissionGroupFormValues } from "@/types/permission";

const SECTION_CLASS_NAME = "space-y-4 rounded-xl border border-border/70 bg-muted/25 p-4";

interface SectionProps {
    children: ReactNode;
    description: string;
    title: string;
}

const Section = ({ children, description, title }: SectionProps) => (
    <section className={SECTION_CLASS_NAME}>
        <div className="space-y-1">
            <h3 className="text-sm font-semibold text-foreground">{title}</h3>
            <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        {children}
    </section>
);

interface PreviewCardProps {
    title: string;
    value?: string | null;
}

const PreviewCard = ({ title, value }: PreviewCardProps) => (
    <div className="rounded-xl border border-border/70 bg-background/55 p-4">
        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">{title}</div>
        <MinecraftTextPreview value={value} className="mt-2 font-mono" />
    </div>
);

interface PermissionGroupFormProps {
    disableIdentityFields?: boolean;
    form: UseFormReturn<PermissionGroupFormValues>;
}

const PermissionGroupForm = ({ disableIdentityFields = false, form }: PermissionGroupFormProps) => {
    const sessionUsername = useAuthStore((state) => state.session?.user.username?.trim() ?? "");
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
    const previewPlayerName = sessionUsername || name.trim() || id.trim() || "Player";
    const previewNameColor = nameColor.trim() ? nameColor : "&7";
    const previewChatPrefix = chatPrefix.trim() ? chatPrefix : "&7[Group] ";
    const previewChatSuffix = chatSuffix.trim() ? chatSuffix : "&7: &f";
    const previewTabPrefix = tabPrefix.trim() ? tabPrefix : "&7";
    const displayPreviewValues = {
        chatPrefix: `${previewChatPrefix}${previewNameColor}${previewPlayerName}`,
        chatSuffix: `${previewNameColor}${previewPlayerName}${previewChatSuffix}Hello`,
        nameColor: `${previewNameColor}${previewPlayerName}`,
        tabPrefix: `${previewTabPrefix}${previewNameColor}${previewPlayerName}`,
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
                                <ToggleGroup
                                    type="single"
                                    variant="outline"
                                    value={field.value ? "enabled" : "disabled"}
                                    onValueChange={(value) => {
                                        if (value === "enabled" || value === "disabled") {
                                            field.onChange(value === "enabled");
                                        }
                                    }}
                                    className="w-full"
                                >
                                    <ToggleGroupItem
                                        value="disabled"
                                        className="h-10 flex-1 rounded-l-lg rounded-r-none"
                                    >
                                        Disabled
                                    </ToggleGroupItem>
                                    <ToggleGroupItem
                                        value="enabled"
                                        className="h-10 flex-1 rounded-l-none rounded-r-lg"
                                    >
                                        Enabled
                                    </ToggleGroupItem>
                                </ToggleGroup>
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
                <FieldGroup className="space-y-4">
                    <Field>
                        <FieldLabel htmlFor="permission-group-chat-prefix">Chat prefix</FieldLabel>
                        <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                            <div className="space-y-2">
                                <Input
                                    id="permission-group-chat-prefix"
                                    {...form.register("display.chatPrefix")}
                                />
                                <FieldDescription>
                                    Text shown before player names in chat.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.display?.chatPrefix]} />
                            </div>
                            <PreviewCard
                                title="Chat prefix preview"
                                value={displayPreviewValues.chatPrefix}
                            />
                        </div>
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-chat-suffix">Chat suffix</FieldLabel>
                        <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                            <div className="space-y-2">
                                <Input
                                    id="permission-group-chat-suffix"
                                    {...form.register("display.chatSuffix")}
                                />
                                <FieldDescription>
                                    Text appended after player names in chat.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.display?.chatSuffix]} />
                            </div>
                            <PreviewCard
                                title="Chat suffix preview"
                                value={displayPreviewValues.chatSuffix}
                            />
                        </div>
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-name-color">Name color</FieldLabel>
                        <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                            <div className="space-y-2">
                                <Input
                                    id="permission-group-name-color"
                                    {...form.register("display.nameColor")}
                                />
                                <FieldDescription>
                                    Color code applied to player names.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.display?.nameColor]} />
                            </div>
                            <PreviewCard
                                title="Name color preview"
                                value={displayPreviewValues.nameColor}
                            />
                        </div>
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="permission-group-tab-prefix">Tab prefix</FieldLabel>
                        <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                            <div className="space-y-2">
                                <Input
                                    id="permission-group-tab-prefix"
                                    {...form.register("display.tabPrefix")}
                                />
                                <FieldDescription>
                                    Prefix displayed in the tab list.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.display?.tabPrefix]} />
                            </div>
                            <PreviewCard
                                title="Tab prefix preview"
                                value={displayPreviewValues.tabPrefix}
                            />
                        </div>
                    </Field>
                </FieldGroup>
            </Section>
        </div>
    );
};

export default PermissionGroupForm;
