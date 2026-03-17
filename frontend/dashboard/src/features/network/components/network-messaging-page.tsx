import { useEffect } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import {
    LoaderCircleIcon,
    MessageSquareTextIcon,
    PanelTopIcon,
    TextIcon,
} from "lucide-react";

import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { useNetworkPageContext } from "@/pages/network/context";
import {
    networkMessagingFormSchema,
    type NetworkMessagingFormValues,
} from "@/features/network/schemas";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
    Field,
    FieldDescription,
    FieldError,
    FieldGroup,
    FieldLabel,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

const PreviewCard = ({
    title,
    value,
}: {
    title: string;
    value?: string | null;
}) => (
    <div className="rounded-xl border border-border/70 bg-background/55 p-4">
        <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">{title}</div>
        <MinecraftTextPreview value={value} className="mt-2 font-mono" />
    </div>
);

const NetworkMessagingPage = () => {
    const { isLoading, reloadData, saveSettings, settings } = useNetworkPageContext();
    const form = useForm<NetworkMessagingFormValues>({
        resolver: zodResolver(networkMessagingFormSchema),
        defaultValues: {
            maintenanceKickMessage: "",
            motdGlobal: "",
            motdMaintenance: "",
            tablistFooter: "",
            tablistHeader: "",
            versionNameGlobal: "",
            versionNameMaintenance: "",
        },
    });

    useEffect(() => {
        if (!settings) {
            return;
        }

        form.reset({
            maintenanceKickMessage: settings.maintenanceKickMessage,
            motdGlobal: settings.motd.global,
            motdMaintenance: settings.motd.maintenance,
            tablistFooter: settings.tablist.footer,
            tablistHeader: settings.tablist.header,
            versionNameGlobal: settings.versionName.global,
            versionNameMaintenance: settings.versionName.maintenance,
        });
    }, [form, settings]);

    const previewValues = useWatch({
        control: form.control,
    });
    const tablistEnabled = settings?.general.tablistEnabled ?? true;

    const handleSave = form.handleSubmit(async (values) => {
        try {
            await saveSettings(
                {
                    maintenanceKickMessage: values.maintenanceKickMessage,
                    motd: {
                        global: values.motdGlobal,
                        maintenance: values.motdMaintenance,
                    },
                    tablist: {
                        footer: values.tablistFooter,
                        header: values.tablistHeader,
                    },
                    versionName: {
                        global: values.versionNameGlobal,
                        maintenance: values.versionNameMaintenance,
                    },
                },
                "Messaging settings saved.",
            );
        } catch (error) {
            form.setError("root", {
                message:
                    error instanceof Error ? error.message : "Unable to save messaging settings.",
            });
        }
    });

    if (!settings && !isLoading) {
        return (
            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>Messaging settings unavailable</CardTitle>
                    <CardDescription>
                        The dashboard could not load MOTD, version name, and tablist values for
                        this network view.
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <Button type="button" variant="outline" onClick={() => void reloadData()}>
                        Retry loading messaging
                    </Button>
                </CardContent>
            </Card>
        );
    }

    return (
        <form onSubmit={handleSave} className="space-y-6">
            <div className="grid gap-6 xl:grid-cols-2">
                <Card className="border-border/70 bg-card/80">
                    <CardHeader>
                        <CardTitle>Connection messaging</CardTitle>
                        <CardDescription>
                            Version names and maintenance kick copy shown at the proxy edge.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="grid gap-6 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
                        <FieldGroup>
                            <Field>
                                <FieldLabel htmlFor="network-version-global">Global version</FieldLabel>
                                <Input
                                    id="network-version-global"
                                    {...form.register("versionNameGlobal", {
                                        onChange: () => form.clearErrors("root"),
                                    })}
                                />
                                <FieldDescription>
                                    Version label shown to players during normal operation.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.versionNameGlobal]} />
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-version-maintenance">
                                    Maintenance version
                                </FieldLabel>
                                <Input
                                    id="network-version-maintenance"
                                    {...form.register("versionNameMaintenance", {
                                        onChange: () => form.clearErrors("root"),
                                    })}
                                />
                                <FieldDescription>
                                    Version label shown while maintenance mode is active.
                                </FieldDescription>
                                <FieldError
                                    errors={[form.formState.errors.versionNameMaintenance]}
                                />
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-maintenance-kick-message">
                                    Maintenance kick message
                                </FieldLabel>
                                <Textarea
                                    id="network-maintenance-kick-message"
                                    rows={5}
                                    {...form.register("maintenanceKickMessage", {
                                        onChange: () => form.clearErrors("root"),
                                    })}
                                />
                                <FieldDescription>
                                    Message shown to players removed while maintenance mode is enabled.
                                </FieldDescription>
                                <FieldError
                                    errors={[form.formState.errors.maintenanceKickMessage]}
                                />
                            </Field>
                        </FieldGroup>

                        <div className="space-y-4">
                            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                                <TextIcon className="size-4 text-primary" />
                                Live preview
                            </div>
                            <PreviewCard
                                title="Global version"
                                value={previewValues.versionNameGlobal}
                            />
                            <PreviewCard
                                title="Maintenance version"
                                value={previewValues.versionNameMaintenance}
                            />
                            <PreviewCard
                                title="Maintenance kick"
                                value={previewValues.maintenanceKickMessage}
                            />
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-border/70 bg-card/80">
                    <CardHeader>
                        <CardTitle>MOTD</CardTitle>
                        <CardDescription>
                            Global and maintenance MOTD copy currently exposed to incoming players.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="grid gap-6 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
                        <FieldGroup>
                            <Field>
                                <FieldLabel htmlFor="network-motd-global">Global MOTD</FieldLabel>
                                <Textarea
                                    id="network-motd-global"
                                    rows={5}
                                    {...form.register("motdGlobal", {
                                        onChange: () => form.clearErrors("root"),
                                    })}
                                />
                                <FieldDescription>
                                    Primary MOTD shown while the network is accepting joins.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.motdGlobal]} />
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-motd-maintenance">
                                    Maintenance MOTD
                                </FieldLabel>
                                <Textarea
                                    id="network-motd-maintenance"
                                    rows={5}
                                    {...form.register("motdMaintenance", {
                                        onChange: () => form.clearErrors("root"),
                                    })}
                                />
                                <FieldDescription>
                                    Alternate MOTD shown while maintenance mode is enabled.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.motdMaintenance]} />
                            </Field>
                        </FieldGroup>

                        <div className="space-y-4">
                            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                                <MessageSquareTextIcon className="size-4 text-primary" />
                                Preview
                            </div>
                            <PreviewCard title="Global MOTD" value={previewValues.motdGlobal} />
                            <PreviewCard
                                title="Maintenance MOTD"
                                value={previewValues.motdMaintenance}
                            />
                        </div>
                    </CardContent>
                </Card>
            </div>

            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <div className="flex flex-wrap items-center gap-2">
                        <CardTitle>Tablist</CardTitle>
                        <Badge
                            variant="outline"
                            className={
                                tablistEnabled
                                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                    : "border-amber-500/30 bg-amber-500/10 text-amber-300"
                            }
                        >
                            {tablistEnabled ? "Enabled" : "Disabled in general settings"}
                        </Badge>
                    </div>
                    <CardDescription>
                        Header and footer templates shown in the in-game tab list. These fields are
                        disabled until tablist support is re-enabled in the General section.
                    </CardDescription>
                </CardHeader>
                <CardContent className="grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
                    <FieldGroup className={!tablistEnabled ? "opacity-60" : undefined}>
                        <Field>
                            <FieldLabel htmlFor="network-tablist-header">Tablist header</FieldLabel>
                            <Textarea
                                id="network-tablist-header"
                                rows={6}
                                disabled={!tablistEnabled}
                                {...form.register("tablistHeader", {
                                    onChange: () => form.clearErrors("root"),
                                })}
                            />
                            <FieldDescription>
                                Header text shown above the player list, including Minecraft formatting codes.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.tablistHeader]} />
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="network-tablist-footer">Tablist footer</FieldLabel>
                            <Textarea
                                id="network-tablist-footer"
                                rows={6}
                                disabled={!tablistEnabled}
                                {...form.register("tablistFooter", {
                                    onChange: () => form.clearErrors("root"),
                                })}
                            />
                            <FieldDescription>
                                Footer text shown beneath the player list.
                            </FieldDescription>
                            <FieldError errors={[form.formState.errors.tablistFooter]} />
                        </Field>
                    </FieldGroup>

                    <div className="space-y-4">
                        <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
                            <PanelTopIcon className="size-4 text-primary" />
                            Tablist preview
                        </div>
                        <PreviewCard title="Header" value={previewValues.tablistHeader} />
                        <PreviewCard title="Footer" value={previewValues.tablistFooter} />
                    </div>
                </CardContent>
            </Card>

            <FieldError>{form.formState.errors.root?.message}</FieldError>

            <div className="flex justify-end">
                <Button type="submit" disabled={form.formState.isSubmitting}>
                    {form.formState.isSubmitting ? (
                        <>
                            <LoaderCircleIcon className="size-4 animate-spin" />
                            Saving
                        </>
                    ) : (
                        "Save changes"
                    )}
                </Button>
            </div>
        </form>
    );
};

export default NetworkMessagingPage;
