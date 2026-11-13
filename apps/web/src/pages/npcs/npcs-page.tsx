import type { NpcView } from "@ogcloud/api-client";
import { useNpcList } from "@ogcloud/api-client/react";
import {
  Button,
  Card,
  columnsFor,
  DataTable,
  EmptyState,
  PageHeader,
} from "@ogcloud/ui";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowUpRight, Bot, Plus, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { loadFailure } from "../../errors.ts";
import { useCan } from "../../permissions.ts";
import { placeholderNpc } from "../../placeholders.ts";
import { ALL_GROUPS, GroupFilter } from "../groups/group-filter.tsx";
import { useBackendGroupIds } from "../groups/group-ids.ts";
import { GroupLink } from "../groups/group-status.tsx";
import { useDeleteNpc } from "./npc-delete.tsx";
import { NpcTitleChip } from "./npc-look.tsx";

const COORDINATE_DIGITS = 1;
const columns = columnsFor<NpcView>();

/** Every managed NPC, by group, with how its title looks and what its clicks do. */
export function NpcsPage() {
  const { t } = useTranslation("web");
  const navigate = useNavigate();
  const canWrite = useCan("npcs:write");
  const [group, setGroup] = useState(ALL_GROUPS);
  const npcs = useNpcList(
    group === ALL_GROUPS ? undefined : { groupId: group },
  );
  const groupIds = useBackendGroupIds();
  const remove = useDeleteNpc();
  const open = (id: string) =>
    navigate({ to: "/npcs/$npcId", params: { npcId: id } });

  const npcColumns = useMemo(
    () =>
      columns.columns([
        columns.accessor("settings.title", {
          header: () => t("npcs.columns.title"),
          cell: (cell) => (
            <Link
              to="/npcs/$npcId"
              params={{ npcId: cell.row.original.id }}
              aria-label={t("npcs.open")}
            >
              <NpcTitleChip title={cell.getValue()} />
            </Link>
          ),
        }),
        columns.accessor("settings.groupId", {
          header: () => t("npcs.columns.group"),
          cell: (cell) => <GroupLink id={cell.getValue()} />,
        }),
        columns.accessor("settings.location", {
          header: () => t("npcs.columns.location"),
          enableSorting: false,
          cell: (cell) => {
            const { world, x, y, z } = cell.getValue();
            return (
              <span className="font-mono text-mono text-text-2">
                {`${world} ${[x, y, z].map((n) => n.toFixed(COORDINATE_DIGITS)).join(" ")}`}
              </span>
            );
          },
        }),
        columns.accessor(
          (npc) =>
            npc.settings.rightClick?.groupId ??
            npc.settings.leftClick?.groupId ??
            null,
          {
            id: "click",
            header: () => t("npcs.columns.click"),
            cell: (cell) => {
              const target = cell.getValue();
              return (
                <span className="text-text-2">
                  {target === null
                    ? t("npcs.form.nothing")
                    : t("npcs.form.moveTo", { group: target })}
                </span>
              );
            },
          },
        ),
      ]),
    [t],
  );

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("nav.npcs")}
        actions={
          canWrite ? (
            <Button
              kind="primary"
              icon={Plus}
              onClick={() => navigate({ to: "/npcs/new" })}
            >
              {t("npcs.create.title")}
            </Button>
          ) : undefined
        }
      />
      <GroupFilter groupIds={groupIds} value={group} onValueChange={setGroup} />
      <Card flush>
        <DataTable
          label={t("nav.npcs")}
          data={npcs.data?.data ?? []}
          failure={loadFailure(npcs)}
          columns={npcColumns}
          getRowId={(npc) => npc.id}
          placeholder={placeholderNpc}
          actions={(npc) => ({
            menu: [
              [
                {
                  label: t("npcs.open"),
                  icon: ArrowUpRight,
                  onSelect: () => open(npc.id),
                },
              ],
              ...(canWrite
                ? [
                    [
                      {
                        label: t("npcs.delete.action"),
                        icon: Trash2,
                        danger: true,
                        onSelect: () => remove.ask(npc.id),
                      },
                    ],
                  ]
                : []),
            ],
          })}
          loading={npcs.isPending}
          empty={
            <EmptyState
              icon={Bot}
              title={t("npcs.empty")}
              description={t("npcs.emptyDescription")}
            />
          }
        />
      </Card>
      {remove.dialog}
    </div>
  );
}
