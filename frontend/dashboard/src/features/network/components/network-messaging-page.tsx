import { useEffect, useState, type ChangeEvent } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm, useWatch } from "react-hook-form";
import { LoaderCircleIcon } from "lucide-react";

import MinecraftTextPreview from "@/components/MinecraftTextPreview";
import { useNetworkPageContext } from "@/features/network/lib/context";
import { NETWORK_MOTD_MAX_LINES, truncateTextToMaxLines } from "@/features/network/lib/utils";
import {
    networkMessagingFormSchema,
    type NetworkMessagingFormValues,
} from "@/features/network/schemas";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import { Field, FieldDescription, FieldError, FieldGroup, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";

type MessagingSaveSection = "connection" | "motd" | "tablist";
type MotdFieldName = "motdGlobal" | "motdMaintenance";

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

const NetworkMessagingPage = () => {
    const { isLoading, saveSettings, settings } = useNetworkPageContext();
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
    const [activeSaveSection, setActiveSaveSection] = useState<MessagingSaveSection | null>(null);

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
    const motdGlobalField = form.register("motdGlobal");
    const motdMaintenanceField = form.register("motdMaintenance");

    const handleMotdChange =
        (fieldName: MotdFieldName) => (event: ChangeEvent<HTMLTextAreaElement>) => {
            const truncatedValue = truncateTextToMaxLines(
                event.target.value,
                NETWORK_MOTD_MAX_LINES,
            );

            if (truncatedValue !== event.target.value) {
                event.target.value = truncatedValue;
            }

            form.setValue(fieldName, truncatedValue, {
                shouldDirty: true,
                shouldTouch: true,
            });
            form.clearErrors("root");
        };

    const saveSection = async (
        section: MessagingSaveSection,
        fields: (keyof NetworkMessagingFormValues)[],
        buildPayload: (values: NetworkMessagingFormValues) => Parameters<typeof saveSettings>[0],
        successMessage: string,
    ) => {
        const isValid = await form.trigger(fields);

        if (!isValid) {
            return;
        }

        setActiveSaveSection(section);
        form.clearErrors("root");

        try {
            await saveSettings(buildPayload(form.getValues()), successMessage);
        } catch (error) {
            form.setError("root", {
                message:
                    error instanceof Error ? error.message : "Unable to save messaging settings.",
            });
        } finally {
            setActiveSaveSection(null);
        }
    };

    const handleSaveConnectionMessaging = async () => {
        await saveSection(
            "connection",
            ["versionNameGlobal", "versionNameMaintenance", "maintenanceKickMessage"],
            (values) => ({
                maintenanceKickMessage: values.maintenanceKickMessage,
                versionName: {
                    global: values.versionNameGlobal,
                    maintenance: values.versionNameMaintenance,
                },
            }),
            "Connection messaging saved.",
        );
    };

    const handleSaveMotd = async () => {
        await saveSection(
            "motd",
            ["motdGlobal", "motdMaintenance"],
            (values) => ({
                motd: {
                    global: values.motdGlobal,
                    maintenance: values.motdMaintenance,
                },
            }),
            "MOTD settings saved.",
        );
    };

    const handleSaveTablist = async () => {
        if (!tablistEnabled) {
            return;
        }

        await saveSection(
            "tablist",
            ["tablistHeader", "tablistFooter"],
            (values) => ({
                tablist: {
                    footer: values.tablistFooter,
                    header: values.tablistHeader,
                },
            }),
            "Tablist settings saved.",
        );
    };

    if (!settings && !isLoading) {
        return (
            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>Messaging settings unavailable</CardTitle>
                    <CardDescription>
                        The dashboard could not load MOTD, version name, and tablist values for this
                        network view.
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    return (
        <div className="space-y-6">
            <div className="grid gap-6 xl:grid-cols-2">
                <Card className="flex h-full flex-col border-border/70 bg-card/80">
                    <CardHeader>
                        <CardTitle>Connection messaging</CardTitle>
                        <CardDescription>
                            Version names and maintenance kick copy shown at the proxy edge.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="flex-1">
                        <FieldGroup className="space-y-4">
                            <Field>
                                <FieldLabel htmlFor="network-version-global">
                                    Global version
                                </FieldLabel>
                                <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                                    <div className="space-y-2">
                                        <Input
                                            id="network-version-global"
                                            {...form.register("versionNameGlobal", {
                                                onChange: () => form.clearErrors("root"),
                                            })}
                                        />
                                        <FieldDescription>
                                            Version label shown to players during normal operation.
                                        </FieldDescription>
                                        <FieldError
                                            errors={[form.formState.errors.versionNameGlobal]}
                                        />
                                    </div>
                                    <PreviewCard
                                        title="Global version preview"
                                        value={previewValues.versionNameGlobal}
                                    />
                                </div>
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-version-maintenance">
                                    Maintenance version
                                </FieldLabel>
                                <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                                    <div className="space-y-2">
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
                                    </div>
                                    <PreviewCard
                                        title="Maintenance version preview"
                                        value={previewValues.versionNameMaintenance}
                                    />
                                </div>
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-maintenance-kick-message">
                                    Maintenance kick message
                                </FieldLabel>
                                <div className="mt-2 space-y-2">
                                    <Textarea
                                        id="network-maintenance-kick-message"
                                        rows={5}
                                        {...form.register("maintenanceKickMessage", {
                                            onChange: () => form.clearErrors("root"),
                                        })}
                                    />
                                    <FieldDescription>
                                        Message shown to players removed while maintenance mode is
                                        enabled.
                                    </FieldDescription>
                                    <PreviewCard
                                        title="Maintenance kick preview"
                                        value={previewValues.maintenanceKickMessage}
                                    />
                                    <FieldError
                                        errors={[form.formState.errors.maintenanceKickMessage]}
                                    />
                                </div>
                            </Field>
                        </FieldGroup>
                    </CardContent>
                    <CardFooter className="justify-end border-t border-border/70 pt-4">
                        <Button
                            type="button"
                            onClick={() => void handleSaveConnectionMessaging()}
                            disabled={activeSaveSection !== null}
                        >
                            {activeSaveSection === "connection" ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Saving
                                </>
                            ) : (
                                "Save changes"
                            )}
                        </Button>
                    </CardFooter>
                </Card>

                <Card className="flex h-full flex-col border-border/70 bg-card/80">
                    <CardHeader>
                        <CardTitle>MOTD</CardTitle>
                        <CardDescription>
                            Global and maintenance MOTD copy currently exposed to incoming players.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="flex-1">
                        <FieldGroup className="space-y-4">
                            <Field>
                                <FieldLabel htmlFor="network-motd-global">Global MOTD</FieldLabel>
                                <div className="mt-2 space-y-2">
                                    <Textarea
                                        id="network-motd-global"
                                        rows={2}
                                        {...motdGlobalField}
                                        onChange={handleMotdChange("motdGlobal")}
                                    />
                                    <FieldDescription>
                                        Primary MOTD shown while the network is accepting joins.
                                        Limited to {NETWORK_MOTD_MAX_LINES} lines.
                                    </FieldDescription>
                                    <PreviewCard
                                        title="Global MOTD preview"
                                        value={previewValues.motdGlobal}
                                    />
                                    <FieldError errors={[form.formState.errors.motdGlobal]} />
                                </div>
                            </Field>

                            <Field>
                                <FieldLabel htmlFor="network-motd-maintenance">
                                    Maintenance MOTD
                                </FieldLabel>
                                <div className="mt-2 space-y-2">
                                    <Textarea
                                        id="network-motd-maintenance"
                                        rows={2}
                                        {...motdMaintenanceField}
                                        onChange={handleMotdChange("motdMaintenance")}
                                    />
                                    <FieldDescription>
                                        Alternate MOTD shown while maintenance mode is enabled.
                                        Limited to {NETWORK_MOTD_MAX_LINES} lines.
                                    </FieldDescription>
                                    <PreviewCard
                                        title="Maintenance MOTD preview"
                                        value={previewValues.motdMaintenance}
                                    />
                                    <FieldError errors={[form.formState.errors.motdMaintenance]} />
                                </div>
                            </Field>
                        </FieldGroup>
                    </CardContent>
                    <CardFooter className="justify-end border-t border-border/70 pt-4">
                        <Button
                            type="button"
                            onClick={() => void handleSaveMotd()}
                            disabled={activeSaveSection !== null}
                        >
                            {activeSaveSection === "motd" ? (
                                <>
                                    <LoaderCircleIcon className="size-4 animate-spin" />
                                    Saving
                                </>
                            ) : (
                                "Save changes"
                            )}
                        </Button>
                    </CardFooter>
                </Card>
            </div>

            <Card className="flex flex-col border-border/70 bg-card/80">
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
                <CardContent className="flex-1">
                    <FieldGroup className={tablistEnabled ? "space-y-4" : "space-y-4 opacity-60"}>
                        <Field>
                            <FieldLabel htmlFor="network-tablist-header">Tablist header</FieldLabel>
                            <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                                <div className="space-y-2">
                                    <Textarea
                                        id="network-tablist-header"
                                        rows={6}
                                        disabled={!tablistEnabled}
                                        {...form.register("tablistHeader", {
                                            onChange: () => form.clearErrors("root"),
                                        })}
                                    />
                                    <FieldDescription>
                                        Header text shown above the player list, including Minecraft
                                        formatting codes.
                                    </FieldDescription>
                                    <FieldError errors={[form.formState.errors.tablistHeader]} />
                                </div>
                                <PreviewCard
                                    title="Header preview"
                                    value={previewValues.tablistHeader}
                                />
                            </div>
                        </Field>

                        <Field>
                            <FieldLabel htmlFor="network-tablist-footer">Tablist footer</FieldLabel>
                            <div className="mt-2 grid gap-4 lg:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)] lg:items-start">
                                <div className="space-y-2">
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
                                </div>
                                <PreviewCard
                                    title="Footer preview"
                                    value={previewValues.tablistFooter}
                                />
                            </div>
                        </Field>
                    </FieldGroup>
                </CardContent>
                <CardFooter className="justify-end border-t border-border/70 pt-4">
                    <Button
                        type="button"
                        onClick={() => void handleSaveTablist()}
                        disabled={activeSaveSection !== null || !tablistEnabled}
                    >
                        {activeSaveSection === "tablist" ? (
                            <>
                                <LoaderCircleIcon className="size-4 animate-spin" />
                                Saving
                            </>
                        ) : (
                            "Save changes"
                        )}
                    </Button>
                </CardFooter>
            </Card>

            <FieldError>{form.formState.errors.root?.message}</FieldError>
        </div>
    );
};

export default NetworkMessagingPage;
