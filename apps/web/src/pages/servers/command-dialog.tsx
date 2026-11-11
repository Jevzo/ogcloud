import type { ConsoleCommandRequestTarget } from "@ogcloud/api-client";
import { useCommandRun } from "@ogcloud/api-client/react";
import {
  Button,
  Dialog,
  Field,
  Input,
  Segmented,
  Select,
  useToast,
} from "@ogcloud/ui";
import { type FormEvent, useState } from "react";
import { useTranslation } from "react-i18next";
import { useFailureToast } from "../../errors.ts";

/** Who a console command goes to: a fixed server, or a choice the dialog offers. */
export type CommandTarget =
  | { kind: "server"; id: string; name: string }
  | { kind: "choose"; groupIds: readonly string[] };

interface CommandDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  target: CommandTarget;
}

type Scope = Exclude<ConsoleCommandRequestTarget, "SERVER">;

/** Runs a console command on one server, a group's servers or every server (D-0101); output stays in the logs. */
export function CommandDialog({
  open,
  onOpenChange,
  target,
}: CommandDialogProps) {
  const { t } = useTranslation("web");
  const { t: ui } = useTranslation("ui");
  const toast = useToast();
  const failed = useFailureToast();
  const [scope, setScope] = useState<Scope>("ALL");
  const [group, setGroup] = useState<string>();
  const [command, setCommand] = useState("");
  const run = useCommandRun();

  const request =
    target.kind === "server"
      ? { target: "SERVER" as const, id: target.id }
      : scope === "GROUP"
        ? group === undefined
          ? undefined
          : { target: "GROUP" as const, id: group }
        : { target: "ALL" as const, id: null };
  const ready = request !== undefined && command.trim() !== "";

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!ready) return;
    run.mutate(
      { data: { ...request, command: command.trim() } },
      {
        onSuccess: () => {
          toast({ message: t("servers.command.sent") });
          setCommand("");
          onOpenChange(false);
        },
        onError: failed,
      },
    );
  };

  return (
    <Dialog
      open={open}
      onOpenChange={onOpenChange}
      title={
        target.kind === "server"
          ? t("servers.command.titleFor", { name: target.name })
          : t("servers.command.title")
      }
      description={t("servers.command.description")}
      footer={
        <>
          <Button kind="secondary" onClick={() => onOpenChange(false)}>
            {ui("actions.cancel")}
          </Button>
          <Button
            kind="primary"
            type="submit"
            form="console-command"
            disabled={!ready}
            loading={run.isPending}
          >
            {t("servers.command.submit")}
          </Button>
        </>
      }
    >
      <form
        id="console-command"
        onSubmit={submit}
        className="flex flex-col gap-3"
      >
        {target.kind === "choose" ? (
          <>
            <Segmented
              label={t("servers.command.scope")}
              options={[
                { value: "ALL", label: t("servers.command.all") },
                { value: "GROUP", label: t("servers.command.group") },
              ]}
              value={scope}
              onValueChange={setScope}
            />
            {scope === "GROUP" ? (
              <Field label={t("servers.request.group")}>
                {(control) => (
                  <Select
                    {...control}
                    options={target.groupIds.map((id) => ({
                      value: id,
                      label: id,
                    }))}
                    value={group}
                    onValueChange={setGroup}
                    placeholder={t("servers.request.pickGroup")}
                  />
                )}
              </Field>
            ) : null}
          </>
        ) : null}
        <Field
          label={t("servers.command.command")}
          hint={t("servers.command.hint")}
        >
          {(control) => (
            <Input
              {...control}
              mono
              autoComplete="off"
              spellCheck={false}
              placeholder={t("servers.command.placeholder")}
              value={command}
              onChange={(event) => setCommand(event.target.value)}
            />
          )}
        </Field>
      </form>
    </Dialog>
  );
}
