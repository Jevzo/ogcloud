import { useEffect } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Controller, useForm, useWatch } from "react-hook-form";
import { LoaderCircleIcon, LockIcon } from "lucide-react";

import { useNetworkPageContext } from "@/features/network/lib/context";
import {
    formatNetworkLockDuration,
    formatProxyRoutingStrategy,
} from "@/features/network/lib/utils";
import {
    networkGeneralFormSchema,
    type NetworkGeneralFormValues,
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
import { PageReveal } from "@/components/ui/page-reveal";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";

const ToggleSettingCard = ({
    badge,
    checked,
    description,
    disabled = false,
    onCheckedChange,
    title,
}: {
    badge?: React.ReactNode;
    checked: boolean;
    description: string;
    disabled?: boolean;
    onCheckedChange: (checked: boolean) => void;
    title: string;
}) => (
    <div className="flex items-start justify-between gap-4 rounded-xl border border-border/70 bg-background/55 p-4">
        <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2">
                <div className="text-sm font-semibold text-foreground">{title}</div>
                {badge}
            </div>
            <div className="text-sm leading-6 text-muted-foreground">{description}</div>
        </div>
        <Switch checked={checked} onCheckedChange={onCheckedChange} disabled={disabled} />
    </div>
);

const NetworkGeneralPage = () => {
    const { isLoading, locks, saveSettings, settings } = useNetworkPageContext();
    const form = useForm<NetworkGeneralFormValues>({
        resolver: zodResolver(networkGeneralFormSchema),
        defaultValues: {
            permissionSystemEnabled: true,
            proxyRoutingStrategy: "LOAD_BASED",
            tablistEnabled: true,
        },
    });

    useEffect(() => {
        if (!settings) {
            return;
        }

        form.reset({
            permissionSystemEnabled: settings.general.permissionSystemEnabled,
            proxyRoutingStrategy: settings.general.proxyRoutingStrategy,
            tablistEnabled: settings.general.tablistEnabled,
        });
    }, [form, settings]);

    const permissionSystemEnabled = useWatch({
        control: form.control,
        name: "permissionSystemEnabled",
    });
    const proxyRoutingStrategy = useWatch({
        control: form.control,
        name: "proxyRoutingStrategy",
    });
    const tablistEnabled = useWatch({
        control: form.control,
        name: "tablistEnabled",
    });

    const permissionLock =
        locks.find((lock) => lock.type.toUpperCase() === "PERMISSION_REENABLE") ?? null;
    const recommendedPreset =
        permissionSystemEnabled && tablistEnabled && proxyRoutingStrategy === "LOAD_BASED";

    const handleSave = form.handleSubmit(async (values) => {
        try {
            await saveSettings(
                {
                    general: {
                        permissionSystemEnabled: values.permissionSystemEnabled,
                        proxyRoutingStrategy: values.proxyRoutingStrategy,
                        tablistEnabled: values.tablistEnabled,
                    },
                },
                "General network settings saved.",
            );
        } catch (error) {
            form.setError("root", {
                message:
                    error instanceof Error
                        ? error.message
                        : "Unable to save general network settings.",
            });
        }
    });

    if (!settings && !isLoading) {
        return (
            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>General settings unavailable</CardTitle>
                    <CardDescription>
                        The dashboard could not load proxy routing and plugin control state.
                    </CardDescription>
                </CardHeader>
            </Card>
        );
    }

    return (
        <PageReveal className="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
            <div className="space-y-6">
                <Card className="border-border/70 bg-card/80">
                    <CardHeader>
                        <div className="flex flex-wrap items-center gap-2">
                            <Badge
                                variant="outline"
                                className={
                                    recommendedPreset
                                        ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-300"
                                        : "border-amber-500/30 bg-amber-500/10 text-amber-300"
                                }
                            >
                                {recommendedPreset ? "Recommended preset" : "Custom preset"}
                            </Badge>
                        </div>
                        <CardTitle>Current behavior</CardTitle>
                        <CardDescription>
                            Snapshot of the general network controls that affect routing, permission
                            synchronization, and player list updates.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="grid gap-3">
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Permission system
                            </div>
                            <div className="mt-2 text-sm font-semibold text-foreground">
                                {permissionSystemEnabled ? "Enabled" : "Disabled"}
                            </div>
                        </div>
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Tablist
                            </div>
                            <div className="mt-2 text-sm font-semibold text-foreground">
                                {tablistEnabled ? "Enabled" : "Disabled"}
                            </div>
                        </div>
                        <div className="rounded-xl border border-border/70 bg-background/55 px-4 py-3">
                            <div className="text-[11px] uppercase tracking-[0.18em] text-muted-foreground">
                                Proxy routing
                            </div>
                            <div className="mt-2 text-sm font-semibold text-foreground">
                                {formatProxyRoutingStrategy(proxyRoutingStrategy ?? "LOAD_BASED")}
                            </div>
                        </div>
                    </CardContent>
                </Card>

                {permissionLock ? (
                    <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-100">
                        <div className="flex items-start gap-3">
                            <LockIcon className="mt-0.5 size-4 shrink-0 text-amber-300" />
                            <div>
                                <div className="font-medium">
                                    Permission synchronization lock active
                                </div>
                                <div className="mt-1 text-amber-100/85">
                                    Wait {formatNetworkLockDuration(permissionLock.ttlSeconds)}{" "}
                                    before toggling the permission system again.
                                </div>
                            </div>
                        </div>
                    </div>
                ) : null}
            </div>

            <Card className="border-border/70 bg-card/80">
                <CardHeader>
                    <CardTitle>Editable configuration</CardTitle>
                    <CardDescription>
                        Keep the default load-balanced routing profile unless operations need a
                        specific alternate behavior.
                    </CardDescription>
                </CardHeader>
                <CardContent className="space-y-6">
                    <form id="network-general-form" onSubmit={handleSave} className="space-y-6">
                        <FieldGroup>
                            <Controller
                                control={form.control}
                                name="permissionSystemEnabled"
                                render={({ field }) => (
                                    <ToggleSettingCard
                                        title="Permission system"
                                        description="Enable network-wide permission injection and synchronization."
                                        checked={field.value}
                                        disabled={Boolean(permissionLock)}
                                        badge={
                                            permissionLock ? (
                                                <Badge
                                                    variant="outline"
                                                    className="border-amber-500/30 bg-amber-500/10 text-amber-300"
                                                >
                                                    Locked
                                                </Badge>
                                            ) : undefined
                                        }
                                        onCheckedChange={(checked) => {
                                            field.onChange(checked);
                                            form.clearErrors("root");
                                        }}
                                    />
                                )}
                            />

                            <Controller
                                control={form.control}
                                name="tablistEnabled"
                                render={({ field }) => (
                                    <ToggleSettingCard
                                        title="Tablist"
                                        description="Enable player-list header, footer, and live tablist updates."
                                        checked={field.value}
                                        onCheckedChange={(checked) => {
                                            field.onChange(checked);
                                            form.clearErrors("root");
                                        }}
                                    />
                                )}
                            />

                            <Field>
                                <FieldLabel htmlFor="network-proxy-routing">
                                    Proxy routing
                                </FieldLabel>
                                <Controller
                                    control={form.control}
                                    name="proxyRoutingStrategy"
                                    render={({ field }) => (
                                        <Select
                                            value={field.value}
                                            onValueChange={(value) => {
                                                field.onChange(value);
                                                form.clearErrors("root");
                                            }}
                                        >
                                            <SelectTrigger
                                                id="network-proxy-routing"
                                                className="w-full"
                                            >
                                                <SelectValue placeholder="Select a routing strategy" />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="LOAD_BASED">
                                                    Load based
                                                </SelectItem>
                                                <SelectItem value="ROUND_ROBIN">
                                                    Round robin
                                                </SelectItem>
                                            </SelectContent>
                                        </Select>
                                    )}
                                />
                                <FieldDescription>
                                    Choose between the least-loaded proxy and a rotating round-robin
                                    edge.
                                </FieldDescription>
                                <FieldError errors={[form.formState.errors.proxyRoutingStrategy]} />
                            </Field>
                        </FieldGroup>
                        <FieldError>{form.formState.errors.root?.message}</FieldError>
                    </form>
                </CardContent>
                <CardFooter className="justify-end gap-2 border-t border-border/70 pt-4">
                    <Button
                        type="button"
                        variant="outline"
                        disabled={
                            !settings || form.formState.isSubmitting || !form.formState.isDirty
                        }
                        onClick={() => {
                            if (!settings) {
                                return;
                            }

                            form.reset({
                                permissionSystemEnabled: settings.general.permissionSystemEnabled,
                                proxyRoutingStrategy: settings.general.proxyRoutingStrategy,
                                tablistEnabled: settings.general.tablistEnabled,
                            });
                        }}
                    >
                        Reset
                    </Button>
                    <Button
                        type="submit"
                        form="network-general-form"
                        disabled={
                            !settings || form.formState.isSubmitting || !form.formState.isDirty
                        }
                    >
                        {form.formState.isSubmitting ? (
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
        </PageReveal>
    );
};

export default NetworkGeneralPage;
